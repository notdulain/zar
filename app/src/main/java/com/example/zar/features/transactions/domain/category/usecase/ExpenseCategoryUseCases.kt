package com.example.zar.features.transactions.domain.category.usecase

import com.example.zar.core.domain.model.LedgerError
import com.example.zar.features.transactions.domain.category.CategoryNameCheck
import com.example.zar.features.transactions.domain.category.CategoryNameProblem
import com.example.zar.features.transactions.domain.category.CategoryNameRules
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.domain.category.repository.ExpenseCategoryRepository
import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** The name was refused before anything was written. */
class CategoryNameRejected(val problem: CategoryNameProblem) : Exception("Category name rejected: $problem")

/**
 * The built-in categories plus the user's own. It starts with the built-ins so a screen is never empty, and falls
 * back to them if the stored categories cannot be read, so entries stay visible with a safe label.
 */
class ObserveExpenseCategoryCatalogUseCase @Inject constructor(
    private val categories: ExpenseCategoryRepository,
) {
    operator fun invoke(): Flow<ExpenseCategoryCatalog> = categories.observeCustomCategories()
        .map(::ExpenseCategoryCatalog)
        .onStart { emit(ExpenseCategoryCatalog.BUILT_IN_ONLY) }
        .catch { emit(ExpenseCategoryCatalog.BUILT_IN_ONLY) }
}

/**
 * Adds a custom expense category. Create [id] once when the person opens "Add category"
 * ([CustomCategoryIds.newId]) and reuse it on every retry: a retry after the first attempt landed finds the
 * category already in [catalog] and writes nothing. The name is checked against [catalog] (built-ins and the user's
 * customs); that is a local check, so two devices can still collide, which the catalog reconciles on read.
 */
class CreateExpenseCategoryUseCase @Inject constructor(
    private val categories: ExpenseCategoryRepository,
) {
    suspend operator fun invoke(
        id: String,
        rawName: String,
        catalog: ExpenseCategoryCatalog,
        now: Instant,
    ): Result<CustomExpenseCategory> {
        if (!CustomCategoryIds.isCustom(id)) return Result.failure(LedgerError.InvalidRecord("Not a custom category ID"))
        catalog.stored.firstOrNull { it.id == id }?.let { return Result.success(it) }
        val valid = when (val check = CategoryNameRules.validate(rawName, catalog)) {
            is CategoryNameCheck.Invalid -> return Result.failure(CategoryNameRejected(check.problem))
            is CategoryNameCheck.Valid -> check
        }
        val category = CustomExpenseCategory(id = id, name = valid.name, nameKey = valid.key, createdAt = now)
        return try {
            categories.create(category)
            Result.success(category)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
