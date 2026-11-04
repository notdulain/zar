package com.example.zar.features.transactions.domain.category

import com.example.zar.core.domain.model.LedgerError
import com.example.zar.features.transactions.domain.category.usecase.CategoryNameRejected
import com.example.zar.features.transactions.domain.category.usecase.CreateExpenseCategoryUseCase
import com.example.zar.features.transactions.domain.category.usecase.ObserveExpenseCategoryCatalogUseCase
import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCategoryUseCasesTest {

    private val repo = FakeExpenseCategoryRepository("alice")
    private val create = CreateExpenseCategoryUseCase(repo)
    private val observe = ObserveExpenseCategoryCatalogUseCase(repo)
    private val now = Instant.parse("2026-10-20T04:00:00Z")

    private fun catalog() = runBlocking { observe().drop(1).first() }

    private fun make(id: String, name: String, c: ExpenseCategoryCatalog = catalog()) =
        runBlocking { create(id, name, c, now) }

    @Test
    fun createsACategoryThatShowsUpInTheCatalog() {
        val result = make("custom-pets-1", "  Pets ")
        assertEquals("Pets", (result.getOrThrow() as CustomExpenseCategory).name)
        assertEquals(listOf("custom-pets-1"), catalog().customs.map { it.id })
        assertEquals(CategoryLabel.Custom("Pets"), catalog().label("custom-pets-1"))
    }

    @Test
    fun theCatalogStartsWithTheBuiltInsBeforeAnythingIsLoaded() {
        assertEquals(ExpenseCategoryCatalog.BUILT_IN_ONLY, runBlocking { observe().first() })
    }

    @Test
    fun aRetryWithTheSameIdIsIdempotent() {
        make("custom-pets-1", "Pets")
        val again = make("custom-pets-1", "Pets")
        // The second attempt finds the first one in the catalog: no second write, same category back.
        assertTrue(again.isSuccess)
        assertEquals(1, repo.writes)
        assertEquals(1, catalog().customs.size)
        // Even a retry that races ahead of the catalog (stale catalog) cannot duplicate or edit.
        val stale = make("custom-pets-1", "Pets", ExpenseCategoryCatalog.BUILT_IN_ONLY)
        assertTrue(stale.isSuccess)
        assertEquals(1, repo.writes)
        assertEquals(1, catalog().customs.size)
    }

    @Test
    fun refusesBadNamesWithoutWriting() {
        make("custom-pets-1", "Pets")
        val refused = listOf("", "x".repeat(41), "pets", "Coffee & dining", "a\nb").map { make("custom-other-${it.length}", it) }
        assertTrue(refused.all { it.exceptionOrNull() is CategoryNameRejected })
        assertEquals(
            listOf(
                CategoryNameProblem.EMPTY, CategoryNameProblem.TOO_LONG, CategoryNameProblem.DUPLICATE,
                CategoryNameProblem.DUPLICATE, CategoryNameProblem.INVALID_CHARACTERS,
            ),
            refused.map { (it.exceptionOrNull() as CategoryNameRejected).problem },
        )
        assertEquals(1, repo.writes)
    }

    @Test
    fun refusesAnIdThatIsNotACustomOne() {
        listOf("groceries", "salary", "transfer-fee").forEach {
            assertTrue(make(it, "Whatever").exceptionOrNull() is LedgerError.InvalidRecord)
        }
        assertEquals(0, repo.writes)
    }

    @Test
    fun aFailedWriteCanBeRetriedWithTheSameId() {
        repo.failNextWith = IllegalStateException("server unreachable")
        val id = CustomCategoryIds.newId()
        assertTrue(make(id, "Pets").isFailure)
        assertEquals(0, catalog().customs.size)
        assertTrue(make(id, "Pets").isSuccess)
        assertEquals(1, repo.writes)
        assertEquals(listOf(id), catalog().customs.map { it.id })
    }

    @Test
    fun signedOutCreateFailsAndWritesNothing() {
        repo.signInAs(null)
        assertTrue(make("custom-pets-1", "Pets").exceptionOrNull() is LedgerError.NotSignedIn)
        assertEquals(0, repo.writes)
    }

    @Test
    fun accountsAreIsolatedAndTheOwnersCatalogReturns() {
        make("custom-pets-1", "Pets")
        repo.signInAs("bob")
        assertEquals(ExpenseCategoryCatalog.BUILT_IN_ONLY, catalog())
        // Bob may use the same name and sees only his own.
        assertTrue(make("custom-pets-2", "Pets").isSuccess)
        assertEquals(listOf("custom-pets-2"), catalog().customs.map { it.id })
        repo.signInAs(null)
        assertEquals(ExpenseCategoryCatalog.BUILT_IN_ONLY, catalog())
        repo.signInAs("alice")
        assertEquals(listOf("custom-pets-1"), catalog().customs.map { it.id })
        // Alice's earlier category is not resolvable under Bob's catalog.
        repo.signInAs("bob")
        assertEquals(CategoryLabel.Unavailable("custom-pets-1"), catalog().label("custom-pets-1"))
    }

    @Test
    fun anOfflineCategoryIsUsableAtOnceWhileTheWriteIsStillQueued() {
        // The repository accepts a write locally and returns; the catalog reflects it immediately.
        val id = CustomCategoryIds.newId()
        make(id, "Pets")
        assertEquals(CategoryLabel.Custom("Pets"), catalog().label(id))
    }

    @Test
    fun aDegradedReadKeepsTheLastKnownNamesAndSaysSo() {
        make("custom-pets-1", "Pets")
        repo.setDegraded(true)
        val degraded = runBlocking { observe().drop(1).first { it.degraded } }
        // The custom name is not replaced by the built-ins, and the state says the read is stale.
        assertEquals(CategoryLabel.Custom("Pets"), degraded.label("custom-pets-1"))
        assertEquals(listOf("custom-pets-1"), degraded.customs.map { it.id })
        assertTrue(degraded.degraded)
    }
}
