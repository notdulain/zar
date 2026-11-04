package com.example.zar.features.transactions.domain.category.usecase

import com.example.zar.core.domain.model.LedgerError
import com.example.zar.features.transactions.domain.category.CategoryNameCheck
import com.example.zar.features.transactions.domain.category.CategoryNameProblem
import com.example.zar.features.transactions.domain.category.CategoryNameRules
import com.example.zar.features.transactions.domain.category.ExpenseCategoryCatalog
import com.example.zar.features.transactions.domain.category.repository.ExpenseCategoryRepository
import com.example.zar.features.transactions.domain.manual.model.CategoryIcon
import com.example.zar.features.transactions.domain.manual.model.CustomCategoryIds
import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** The name was refused before anything was written. */
class CategoryNameRejected(val problem: CategoryNameProblem) : Exception("Category name rejected: $problem")

/**
 * The built-in categories plus the user's own. It starts with the built-ins so a screen is never empty, and keeps
 * the last known catalog if the live read fails: the repository retries in the background and marks the catalog
 * [ExpenseCategoryCatalog.degraded], so the user's own names stay on screen instead of being replaced by built-ins.
 */
class ObserveExpenseCategoryCatalogUseCase @Inject constructor(
    private val categories: ExpenseCategoryRepository,
) {
    operator fun invoke(): Flow<ExpenseCategoryCatalog> = flow {
        var last = ExpenseCategoryCatalog.BUILT_IN_ONLY
        emit(last)
        try {
            categories.observeCustomCategories().collect { snapshot ->
                last = ExpenseCategoryCatalog(snapshot.categories, degraded = snapshot.isDegraded, hiddenBuiltIns = snapshot.hiddenBuiltIns)
                emit(last)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The repository retries transient failures, so this is a fatal read failure: keep the last known
            // catalog rather than falling back to built-ins and losing the user's own category names.
            emit(last)
        }
    }
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
    /** A category [invoke] reported as saved that the server then refused; see [ExpenseCategoryRepository.lateRejections]. */
    val lateRejections: Flow<CustomExpenseCategory> get() = categories.lateRejections

    /**
     * Deletes a custom category the person added. It is archived, not erased: it leaves the pickers and its name can
     * be used again, but entries that used it keep their label. Built-ins and unknown IDs are refused.
     */
    suspend fun archive(id: String): Result<Unit> {
        if (!CustomCategoryIds.isCustom(id)) return Result.failure(LedgerError.InvalidRecord("Not a custom category ID"))
        return try {
            categories.archive(id)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend operator fun invoke(
        id: String,
        rawName: String,
        catalog: ExpenseCategoryCatalog,
        now: Instant,
        icon: CategoryIcon = CategoryIcon.DEFAULT,
    ): Result<CustomExpenseCategory> {
        if (!CustomCategoryIds.isCustom(id)) return Result.failure(LedgerError.InvalidRecord("Not a custom category ID"))
        catalog.stored.firstOrNull { it.id == id }?.let { return Result.success(it) }
        val valid = when (val check = CategoryNameRules.validate(rawName, catalog)) {
            is CategoryNameCheck.Invalid -> return Result.failure(CategoryNameRejected(check.problem))
            is CategoryNameCheck.Valid -> check
        }
        val category = CustomExpenseCategory(id = id, name = valid.name, nameKey = valid.key, createdAt = now, icon = icon)
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
