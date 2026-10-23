package com.example.zar.features.allowance

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.allowance.data.repository.AllowanceDocumentMapper
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.allowance.domain.usecase.ObserveAllowanceUseCase
import com.example.zar.features.allowance.domain.usecase.SaveAllowanceSettingsUseCase
import com.example.zar.features.freelance.FakeExpectedPaymentRepository
import com.example.zar.features.home.StaticLedgerRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowanceUseCasesTest {

    private val today = LocalDate.of(2026, 10, 21)
    private val repo = FakeAllowanceRepository()
    private val ledger = StaticLedgerRepository()
    private val expected = FakeExpectedPaymentRepository()
    private val observe = ObserveAllowanceUseCase(repo, ledger, expected)
    private val save = SaveAllowanceSettingsUseCase(repo)

    @Test
    fun noSavedSettingMeansOffAndNoStatus() = runTest {
        val overview = observe(today).first()
        assertFalse(overview.settings.isEnabled)
        assertNull(overview.status)
    }

    @Test
    fun savingTurnsItOnAndTheStatusFollowsSavedRecords() = runTest {
        assertTrue(save(AllowanceSettings(isEnabled = true, amount = lkr(5_000))).isSuccess)
        ledger.entries.value = LedgerProjector.project(
            listOf(tx("lunch", TransactionType.EXPENSE, lkr(1_200), date = today)), emptyList(), emptyList(),
        )

        val status = observe(today).first().status!!
        assertEquals(lkr(1_200), status.spent.amount)
        assertEquals(lkr(3_800), status.remaining)
    }

    @Test
    fun theStatusUpdatesWhenANewExpenseIsSaved() = runTest {
        save(AllowanceSettings(isEnabled = true, amount = lkr(5_000)))
        assertEquals(lkr(5_000), observe(today).first().status!!.remaining)

        ledger.entries.value = LedgerProjector.project(
            listOf(tx("dinner", TransactionType.EXPENSE, lkr(2_000), date = today)), emptyList(), emptyList(),
        )
        assertEquals(lkr(3_000), observe(today).first().status!!.remaining)
    }

    @Test
    fun turningItOffKeepsTheAmountAndCategoriesForNextTime() = runTest {
        val on = AllowanceSettings(true, lkr(5_000), setOf("groceries"), DayOfWeek.SUNDAY)
        save(on)
        save(on.copy(isEnabled = false))

        val saved = repo.settings.value
        assertFalse(saved.isEnabled)
        assertEquals(lkr(5_000), saved.amount)
        assertEquals(setOf("groceries"), saved.categoryIds)
        assertEquals(DayOfWeek.SUNDAY, saved.weekStart)
        assertNull(observe(today).first().status)
    }

    @Test
    fun anUnknownCategoryIsRejectedAndNothingIsSaved() = runTest {
        val result = save(AllowanceSettings(true, lkr(5_000), setOf("not-a-category")))
        assertTrue(result.isFailure)
        assertEquals(0, repo.saveCount)
    }

    @Test
    fun aFailedSaveIsReportedNotThrown() = runTest {
        repo.failSaves = true
        assertTrue(save(AllowanceSettings(true, lkr(5_000))).isFailure)
    }

    // --- Stored shape -------------------------------------------------------------------

    @Test
    fun settingsRoundTripThroughTheirFirestoreMap() {
        val settings = AllowanceSettings(true, lkr(8_000), setOf("groceries", "coffee-dining"), DayOfWeek.SATURDAY)
        val map = AllowanceDocumentMapper.toMap(settings, Instant.ofEpochMilli(1_790_000_000_000))

        assertEquals("weekly", map["id"])
        assertEquals(listOf("coffee-dining", "groceries"), map["categoryIds"])
        assertEquals(1_790_000_000_000, map["updatedAt"])
        assertEquals(settings, AllowanceDocumentMapper.fromMap(map))
    }

    @Test
    fun anOffSettingWithNoAmountRoundTrips() {
        val map = AllowanceDocumentMapper.toMap(AllowanceSettings.Disabled, Instant.EPOCH.plusMillis(1))
        assertFalse("amount" in map)
        assertEquals(AllowanceSettings.Disabled, AllowanceDocumentMapper.fromMap(map))
    }

    @Test
    fun malformedDocumentsAreIgnoredNotFatal() {
        assertNull(AllowanceDocumentMapper.fromMap(mapOf("enabled" to "yes")))
        assertNull(AllowanceDocumentMapper.fromMap(mapOf("enabled" to true, "categoryIds" to emptyList<String>(), "weekStart" to "FUNDAY")))
        // On with no amount cannot be built, so it is treated as malformed rather than crashing.
        assertNull(
            AllowanceDocumentMapper.fromMap(
                mapOf("enabled" to true, "categoryIds" to emptyList<String>(), "weekStart" to "MONDAY"),
            ),
        )
        assertNotNull(
            AllowanceDocumentMapper.fromMap(
                mapOf("enabled" to false, "categoryIds" to emptyList<String>(), "weekStart" to "MONDAY"),
            ),
        )
    }
}
