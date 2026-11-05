package com.example.zar.features.transactions.domain.category

import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.UserId
import com.example.zar.features.transactions.domain.category.repository.CustomCategorySnapshot
import com.example.zar.features.transactions.domain.category.repository.ExpenseCategoryRepository
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Categories kept per user, like users/{uid}/expenseCategories. Like the firestore.rules, creating an ID that
 * already holds identical data changes nothing and different data is refused. [writes] counts accepted writes.
 */
class FakeExpenseCategoryRepository(user: String? = "alice") : ExpenseCategoryRepository {
    val signedIn = MutableStateFlow(user?.let(::UserId))
    private val stored = MutableStateFlow<Map<String, Map<String, CustomExpenseCategory>>>(emptyMap())
    private val degraded = MutableStateFlow(false)
    private val hidden = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    var failNextWith: Throwable? = null
    var writes = 0
        private set

    /** Marks the next emissions as a stale-but-usable read, as a listener failure would. */
    fun setDegraded(value: Boolean) { degraded.value = value }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCustomCategories(): Flow<CustomCategorySnapshot> = signedIn.flatMapLatest { uid ->
        combine(stored, degraded, hidden) { all, isDegraded, hiddenByUser ->
            CustomCategorySnapshot(
                uid?.let { all[it.value].orEmpty().values.toList() }.orEmpty(),
                isDegraded,
                uid?.let { hiddenByUser[it.value].orEmpty() }.orEmpty(),
            )
        }
    }

    override suspend fun currentCustomCategories(): List<CustomExpenseCategory> =
        signedIn.value?.let { stored.value[it.value].orEmpty().values.toList() }.orEmpty()

    override suspend fun create(category: CustomExpenseCategory) {
        val uid = signedIn.value ?: throw LedgerError.NotSignedIn()
        failNextWith?.let { failNextWith = null; throw it }
        val mine = stored.value[uid.value].orEmpty()
        val existing = mine[category.id]
        if (existing != null) {
            require(existing == category) { "PERMISSION_DENIED: a category is never edited" }
            return
        }
        writes++
        stored.value = stored.value + (uid.value to (mine + (category.id to category)))
    }

    /** Like the rules: the category stays stored, marked archived. Archiving twice, or an unknown ID, changes nothing. */
    override suspend fun archive(id: String) {
        val uid = signedIn.value ?: throw LedgerError.NotSignedIn()
        failNextWith?.let { failNextWith = null; throw it }
        val mine = stored.value[uid.value].orEmpty()
        val existing = mine[id] ?: return
        if (existing.archived) return
        archives++
        stored.value = stored.value + (uid.value to (mine + (id to existing.copy(archived = true))))
    }

    var archives = 0
        private set

    override suspend fun hideBuiltIn(id: String) {
        val uid = signedIn.value ?: throw LedgerError.NotSignedIn()
        failNextWith?.let { failNextWith = null; throw it }
        val mine = hidden.value[uid.value].orEmpty()
        if (id in mine) return
        hides++
        hidden.value = hidden.value + (uid.value to (mine + id))
    }

    override suspend fun restoreBuiltIn(id: String) {
        val uid = signedIn.value ?: throw LedgerError.NotSignedIn()
        failNextWith?.let { failNextWith = null; throw it }
        val mine = hidden.value[uid.value].orEmpty()
        if (id !in mine) return
        restores++
        hidden.value = hidden.value + (uid.value to (mine - id))
    }

    var hides = 0
        private set
    var restores = 0
        private set

    private val late = MutableSharedFlow<CustomExpenseCategory>(extraBufferCapacity = 8)
    override val lateRejections: Flow<CustomExpenseCategory> = late

    /** The server refuses an already-accepted create after it returned: the local copy is rolled back and reported. */
    fun rejectLate(id: String) {
        val uid = signedIn.value ?: return
        val category = stored.value[uid.value].orEmpty()[id] ?: return
        stored.value = stored.value + (uid.value to (stored.value[uid.value].orEmpty() - id))
        late.tryEmit(category)
    }

    fun signInAs(user: String?) { signedIn.value = user?.let(::UserId) }
}
