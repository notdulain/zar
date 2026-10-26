package com.example.zar.features.transactions.domain.category.repository

import com.example.zar.features.transactions.domain.manual.model.CustomExpenseCategory
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in user's custom expense categories. Built-ins are not stored. Implementations scope every read and
 * write to the signed-in user, keep working from the offline cache, and emit an empty list for a signed-out user
 * or while another account's data would otherwise still be showing.
 */
interface ExpenseCategoryRepository {
    /** Live custom categories, in no particular order. [ExpenseCategoryCatalog] orders and reconciles them. */
    fun observeCustomCategories(): Flow<List<CustomExpenseCategory>>

    /**
     * The categories as last known on this device (the local cache), read once and never waiting for the network.
     * For one-off checks such as confirming a quick-note proposal. Empty for a signed-out user.
     */
    suspend fun currentCustomCategories(): List<CustomExpenseCategory>

    /**
     * Stores [category] under its own ID. Saving the same ID again (a retry or an offline replay) changes nothing.
     * Returns once the write is accepted locally; throws if the server rejects it quickly or nobody is signed in
     * (LedgerError.NotSignedIn).
     */
    suspend fun create(category: CustomExpenseCategory)
}
