package com.example.zar.features.accounts.presentation.screen

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.zar.R
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.accounts.domain.model.AccountProblem

@DrawableRes
internal fun AccountType.iconRes(): Int = when (this) {
    AccountType.CASH -> R.drawable.ic_ph_wallet
    AccountType.BANK -> R.drawable.ic_ph_bank
    AccountType.FREELANCE -> R.drawable.ic_ph_briefcase
    AccountType.CRYPTO -> R.drawable.ic_ph_currency_btc
}

@StringRes
internal fun AccountType.labelRes(): Int = when (this) {
    AccountType.CASH -> R.string.account_type_cash
    AccountType.BANK -> R.string.account_type_bank
    AccountType.FREELANCE -> R.string.account_type_freelance
    AccountType.CRYPTO -> R.string.account_type_crypto
}

@StringRes
internal fun AccountType.longLabelRes(): Int = when (this) {
    AccountType.CASH -> R.string.account_type_cash_long
    AccountType.BANK -> R.string.account_type_bank_long
    AccountType.FREELANCE -> R.string.account_type_freelance_long
    AccountType.CRYPTO -> R.string.account_type_crypto_long
}

@StringRes
internal fun TransactionType.labelRes(): Int = when (this) {
    TransactionType.INCOME -> R.string.account_entry_income
    TransactionType.EXPENSE -> R.string.account_entry_expense
    TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT -> R.string.account_entry_transfer
    TransactionType.REFUND -> R.string.account_entry_refund
    TransactionType.ADJUSTMENT_IN, TransactionType.ADJUSTMENT_OUT -> R.string.account_entry_adjustment
}

@DrawableRes
internal fun TransactionType.iconRes(): Int = when (this) {
    TransactionType.INCOME, TransactionType.REFUND, TransactionType.ADJUSTMENT_IN -> R.drawable.ic_ph_wallet
    TransactionType.EXPENSE, TransactionType.ADJUSTMENT_OUT -> R.drawable.ic_ph_receipt
    TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT -> R.drawable.ic_ph_arrows_left_right
}

@StringRes
internal fun Set<AccountProblem>.nameErrorRes(): Int? = when {
    AccountProblem.NAME_MISSING in this -> R.string.account_error_name_missing
    AccountProblem.NAME_TOO_LONG in this -> R.string.account_error_name_too_long
    else -> null
}

@StringRes
internal fun Set<AccountProblem>.openingErrorRes(): Int? = when {
    AccountProblem.OPENING_MISSING in this -> R.string.account_error_opening_missing
    AccountProblem.OPENING_INVALID in this -> R.string.account_error_opening_invalid
    AccountProblem.OPENING_NOT_ALLOWED in this -> R.string.account_error_opening_not_allowed
    else -> null
}

@StringRes
internal fun Set<AccountProblem>.typeErrorRes(): Int? =
    if (AccountProblem.TYPE_CURRENCY_MISMATCH in this) R.string.account_error_type_currency else null
