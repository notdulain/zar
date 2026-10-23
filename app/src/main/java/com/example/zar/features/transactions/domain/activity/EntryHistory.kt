package com.example.zar.features.transactions.domain.activity

import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.Transaction
import com.example.zar.features.transactions.domain.activity.model.CorrectedField
import com.example.zar.features.transactions.domain.activity.model.FieldChange
import com.example.zar.features.transactions.domain.activity.model.FieldValue
import com.example.zar.features.transactions.domain.activity.model.HistoryEvent

/** Reads the append-only corrections back as a list of what changed, so nothing is ever lost from view. */
object EntryHistory {

    /** Applied corrections, oldest first, each with the fields it actually changed. */
    fun build(entry: LedgerEntry, accounts: List<Account> = emptyList()): List<HistoryEvent> {
        var state = entry.original
        return entry.corrections.map { correction ->
            val next = LedgerProjector.applyChanges(state, correction.changes)
            HistoryEvent(correction, changesBetween(state, next, accounts)).also { state = next }
        }
    }

    private fun changesBetween(before: Transaction, after: Transaction, accounts: List<Account>): List<FieldChange> = buildList {
        if (before.accountId != after.accountId) {
            fun name(id: AccountId) = accounts.firstOrNull { it.id == id }?.name ?: id.value
            add(FieldChange(CorrectedField.ACCOUNT, FieldValue.AccountName(name(before.accountId)), FieldValue.AccountName(name(after.accountId))))
        }
        if (before.amount != after.amount) {
            add(FieldChange(CorrectedField.AMOUNT, FieldValue.Amount(before.amount), FieldValue.Amount(after.amount)))
        }
        if (before.time != after.time) {
            add(FieldChange(CorrectedField.TIME, FieldValue.Time(before.time), FieldValue.Time(after.time)))
        }
        if (before.date != after.date) {
            add(FieldChange(CorrectedField.DATE, FieldValue.Day(before.date), FieldValue.Day(after.date)))
        }
        if (before.categoryId != after.categoryId) {
            add(FieldChange(CorrectedField.CATEGORY, FieldValue.Text(before.categoryId), FieldValue.Text(after.categoryId)))
        }
        if (before.spendingClass != after.spendingClass) {
            add(FieldChange(CorrectedField.SPENDING_CLASS, FieldValue.Class(before.spendingClass), FieldValue.Class(after.spendingClass)))
        }
        if (before.merchant != after.merchant) {
            add(FieldChange(CorrectedField.TITLE, FieldValue.Text(before.merchant), FieldValue.Text(after.merchant)))
        }
        if (before.note != after.note) {
            add(FieldChange(CorrectedField.NOTE, FieldValue.Text(before.note), FieldValue.Text(after.note)))
        }
    }
}
