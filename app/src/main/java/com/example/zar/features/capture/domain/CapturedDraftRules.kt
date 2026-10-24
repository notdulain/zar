package com.example.zar.features.capture.domain

import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.capture.domain.model.ParsedBankSms
import com.example.zar.features.capture.domain.model.SmsTransactionKind
import com.example.zar.features.rules.domain.model.CategoryRule
import java.security.MessageDigest
import java.time.Instant

/**
 * Turns a parsed bank message into a pending draft, and a reviewed draft into a record. Only
 * structured fields are kept: the message text itself is never stored or uploaded.
 */
object CapturedDraftRules {

    /** Types a captured draft can be confirmed as here; anything else is added manually. */
    val CONFIRMABLE_TYPES = listOf(TransactionType.EXPENSE, TransactionType.INCOME)

    /**
     * The same message always gives the same ID, so sharing it twice (or replaying a save)
     * can't create two drafts. The ID is a one-way digest and contains no plaintext.
     */
    fun draftIdFor(messageText: String): DraftId {
        val normalized = messageText.replace(Regex("""\s+"""), " ").trim()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return DraftId("capture-" + digest.take(16).joinToString("") { "%02x".format(it) })
    }

    fun draftFrom(id: DraftId, parsed: ParsedBankSms, source: DraftSource, createdAt: Instant): Draft {
        val (type, typeCertain) = suggestedType(parsed.kind)
        val uncertain = buildSet {
            if (!typeCertain) add(DraftField.TYPE)
        }
        val proposal = DraftProposal.ActualTransaction(
            type = type,
            amount = parsed.amount,
            // Messages only show the last digits, which accounts don't store; the user picks.
            accountId = null,
            date = parsed.date,
            spendingClass = if (type == TransactionType.EXPENSE) SpendingClass.UNKNOWN else null,
            merchant = parsed.counterparty?.take(120),
            uncertainFields = uncertain,
            direction = parsed.direction,
        )
        return Draft(id, source, listOf(proposal), DraftStatus.PENDING_REVIEW, createdAt)
    }

    /**
     * What a message most likely is, and whether that's certain. Purchases and fees are
     * expenses. Debits that may be transfers, and every credit, need the user to choose:
     * a credit could be income, a refund or a transfer, and is never assumed to be income.
     */
    fun suggestedType(kind: SmsTransactionKind): Pair<TransactionType?, Boolean> = when (kind) {
        SmsTransactionKind.CARD_PURCHASE, SmsTransactionKind.ONLINE_PURCHASE, SmsTransactionKind.BANK_FEE ->
            TransactionType.EXPENSE to true
        SmsTransactionKind.ATM_WITHDRAWAL, SmsTransactionKind.ONLINE_TRANSFER, SmsTransactionKind.ACCOUNT_DEBIT ->
            null to false
        SmsTransactionKind.ACCOUNT_CREDIT, SmsTransactionKind.REVERSAL -> null to false
    }

    /** The record saved when the user confirms. Its ID comes from the draft, so confirming twice saves once. */
    fun transactionFrom(
        draft: Draft,
        account: Account,
        type: TransactionType,
        spendingClass: SpendingClass?,
        createdAt: Instant,
        /** A category rule that matched this expense. Only an expense takes its category. */
        rule: CategoryRule? = null,
    ): Transaction {
        require(draft.source in setOf(DraftSource.SHARED_TEXT, DraftSource.SMS))
        require(draft.status == DraftStatus.PENDING_REVIEW) { "Draft already reviewed" }
        if (account.isArchived) throw LedgerError.AccountArchived(account.id)
        val proposal = draft.proposals.single() as DraftProposal.ActualTransaction
        val amount = requireNotNull(proposal.amount) { "The draft has no amount" }
        val date = requireNotNull(proposal.date) { "The draft has no date" }
        if (amount.currency != account.currency) throw LedgerError.CurrencyMismatch(account.currency.code, amount.currency.code)
        require(!date.isAfter(createdAt.atZone(ZarTime.ZONE).toLocalDate())) { "A recorded payment cannot be in the future" }
        require(type in CONFIRMABLE_TYPES) { "Add ${type.name.lowercase()} records manually" }
        val filedBy = rule?.takeIf { type == TransactionType.EXPENSE }
        return Transaction(
            id = TransactionId(draft.recordIdFor(0)),
            accountId = account.id,
            type = type,
            amount = amount,
            date = date,
            reportingConversion = CurrencyConverter.toReporting(amount, null),
            spendingClass = if (type == TransactionType.EXPENSE) spendingClass ?: SpendingClass.UNKNOWN else null,
            merchant = proposal.merchant,
            categoryId = filedBy?.categoryId,
            source = if (draft.source == DraftSource.SMS) RecordSource.SMS else RecordSource.SHARED_TEXT,
            createdAt = createdAt,
            appliedRuleId = filedBy?.id?.value,
            appliedRuleVersion = filedBy?.version,
        )
    }
}
