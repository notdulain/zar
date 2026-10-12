package com.example.zar.core.domain.model

/** Reasons a ledger save is rejected before it reaches storage. */
sealed class LedgerError(message: String) : Exception(message) {
    class NotSignedIn : LedgerError("No signed-in user")
    class CurrencyMismatch(expected: String, actual: String) :
        LedgerError("Amount is in $actual but the account uses $expected")
    class AccountArchived(accountId: AccountId) : LedgerError("Account ${accountId.value} is archived")
    class AlreadyVoided(transactionId: TransactionId) :
        LedgerError("Transaction ${transactionId.value} was undone and cannot change")
    class NothingToChange : LedgerError("The correction does not change anything")
    class InvalidRecord(reason: String) : LedgerError(reason)
}
