package com.example.zar.features.transactions.domain.category

import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.UserId
import com.example.zar.features.transactions.domain.category.repository.ExpenseCategoryRepository
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Categories kept per user, like users/{uid}/expenseCategories. Like the firestore.rules, creating an ID that
 * already holds identical data changes nothing and different data is refused. [writes] counts accepted writes.
 */
class FakeExpenseCategoryRepository(user: String? = "alice") : ExpenseCategoryRepository {
    val signedIn = MutableStateFlow(user?.let(::UserId))
    private val stored = MutableStateFlow<Map<String, Map<String, CustomExpenseCategory>>>(emptyMap())
    var failNextWith: Throwable? = null
    var writes = 0
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCustomCategories(): Flow<List<CustomExpenseCategory>> = signedIn.flatMapLatest { uid ->
        stored.map { all -> uid?.let { all[it.value].orEmpty().values.toList() }.orEmpty() }
    }

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

    fun signInAs(user: String?) { signedIn.value = user?.let(::UserId) }
}
