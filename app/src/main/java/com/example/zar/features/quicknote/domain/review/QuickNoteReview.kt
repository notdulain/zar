package com.example.zar.features.quicknote.domain.review

import com.example.zar.core.domain.calculation.CurrencyConverter
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.ExpectedPayment
import com.example.zar.core.domain.model.ExpectedPaymentId
import com.example.zar.core.domain.model.ExpectedSource
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.RecordSource
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.transactions.domain.manual.ManualEntryRules
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.domain.refund.RefundFormInput
import com.example.zar.features.transactions.domain.refund.RefundFormResult
import com.example.zar.features.transactions.domain.refund.RefundProblem
import com.example.zar.features.transactions.domain.refund.RefundRules
import java.time.Instant
import java.time.LocalDate

/** Why a proposal cannot be saved yet. Each one is something the person has to fill in or fix. */
enum class ProposalBlocker {
    TYPE_MISSING,
    DIRECTION_MISSING,
    AMOUNT_MISSING,
    AMOUNT_INVALID,
    ACCOUNT_MISSING,
    CURRENCY_MISMATCH,
    DATE_MISSING,
    DATE_IN_FUTURE,
    TITLE_MISSING,
    PURCHASE_MISSING,
    REFUND_TOO_HIGH,
    FROM_MISSING,
    TO_MISSING,
    SAME_ACCOUNT,
    RECEIVED_MISSING,
    RECEIVED_MISMATCH,
}

/**
 * One proposal as the person is editing it. Amounts are text, like the other forms, so a half-typed value is
 * never lost. [currency] is what the note itself stated (null when it did not say): an account in another
 * currency cannot silently reinterpret the number.
 */
sealed interface ProposalInput {
    /** Fields the service guessed. They stay highlighted until the person touches them. */
    val uncertain: Set<DraftField>

    data class Actual(
        val type: TransactionType?,
        val amount: String,
        val currency: Currency?,
        val accountId: AccountId?,
        val date: LocalDate?,
        val categoryId: String?,
        val spendingClass: SpendingClass?,
        val title: String,
        val note: String,
        /** The purchase a refund returns money for. The generated draft cannot know it. */
        val refundOf: TransactionId?,
        override val uncertain: Set<DraftField>,
    ) : ProposalInput

    data class Expected(
        val direction: Direction?,
        val amount: String,
        val currency: Currency?,
        val dueDate: LocalDate?,
        val title: String,
        val accountId: AccountId?,
        override val uncertain: Set<DraftField>,
    ) : ProposalInput

    data class Transfer(
        val fromId: AccountId?,
        val toId: AccountId?,
        val amount: String,
        val currency: Currency?,
        /** The stated received amount. Blank may use the sent amount for accounts in one currency. */
        val received: String,
        val date: LocalDate?,
        override val uncertain: Set<DraftField>,
        val receivedCurrency: Currency? = null,
    ) : ProposalInput
}

/** What saving a proposal writes. */
sealed interface ConfirmableRecord {
    data class Ledger(val transaction: Transaction, val account: Account) : ConfirmableRecord
    data class Moved(val transfer: Transfer, val from: Account, val to: Account) : ConfirmableRecord
    data class Expectation(val payment: ExpectedPayment) : ConfirmableRecord
}

/** Empty [blockers] means [record] is ready to save. */
data class ProposalCheck(val blockers: Set<ProposalBlocker>, val record: ConfirmableRecord?)

/**
 * Pure rules for reviewing the proposals a quick note produced. Nothing is invented: a missing account, amount
 * or date stays a blocker until the person fills it in, and an expected payment never becomes an actual one.
 * Record IDs come from [Draft.recordIdFor], so saving the same proposal twice saves it once.
 */
object QuickNoteReviewRules {
    private const val MAX_TITLE = ManualEntryRules.MAX_TITLE
    private const val MAX_NOTE = ManualEntryRules.MAX_NOTE

    fun inputFrom(proposal: DraftProposal): ProposalInput = when (proposal) {
        is DraftProposal.ActualTransaction -> ProposalInput.Actual(
            type = proposal.type,
            amount = proposal.amount.asText(),
            currency = proposal.amount?.currency,
            accountId = proposal.accountId,
            date = proposal.date,
            categoryId = proposal.categoryId,
            spendingClass = proposal.spendingClass,
            title = proposal.title ?: proposal.merchant.orEmpty(),
            note = proposal.note.orEmpty(),
            refundOf = null,
            uncertain = proposal.uncertainFields,
        )
        is DraftProposal.Expected -> ProposalInput.Expected(
            direction = proposal.direction,
            amount = proposal.amount.asText(),
            currency = proposal.amount?.currency,
            dueDate = proposal.dueDate,
            title = proposal.title.orEmpty(),
            accountId = proposal.accountId,
            uncertain = proposal.uncertainFields,
        )
        is DraftProposal.TransferBetweenAccounts -> ProposalInput.Transfer(
            fromId = proposal.fromAccountId,
            toId = proposal.toAccountId,
            amount = proposal.sent.asText(),
            currency = proposal.sent?.currency,
            received = proposal.received.asText(),
            date = proposal.date,
            uncertain = proposal.uncertainFields,
            receivedCurrency = proposal.received?.currency,
        )
    }

    private fun Money?.asText(): String = this?.toDecimal()?.stripTrailingZeros()?.toPlainString().orEmpty()

    /**
     * Purchases a refund can point at: active expenses that still have something left to refund. When
     * [accountId] is given only that account's purchases are listed. Newest first, at most [limit].
     */
    fun refundCandidates(entries: List<LedgerEntry>, accountId: AccountId?, limit: Int = 20): List<LedgerEntry> =
        entries
            .filter { !it.isVoided && it.current.type == TransactionType.EXPENSE }
            .filter { accountId == null || it.current.accountId == accountId }
            .filter { RefundRules.summary(it, entries).canRefund }
            .sortedWith(compareByDescending<LedgerEntry> { it.current.date }.thenByDescending { it.current.createdAt })
            .take(limit)

    /** The blockers for [input], and the record to save when there are none. [accounts] are the active ones. */
    fun check(
        draft: Draft,
        index: Int,
        input: ProposalInput,
        accounts: List<Account>,
        entries: List<LedgerEntry>,
        today: LocalDate,
        now: Instant,
    ): ProposalCheck = when (input) {
        is ProposalInput.Actual -> checkActual(draft, index, input, accounts, entries, today, now)
        is ProposalInput.Expected -> checkExpected(draft, index, input, accounts, now)
        is ProposalInput.Transfer -> checkTransfer(draft, index, input, accounts, today, now)
    }

    private fun checkActual(
        draft: Draft,
        index: Int,
        input: ProposalInput.Actual,
        accounts: List<Account>,
        entries: List<LedgerEntry>,
        today: LocalDate,
        now: Instant,
    ): ProposalCheck {
        val blockers = mutableSetOf<ProposalBlocker>()
        val type = input.type
        if (type == null) blockers += ProposalBlocker.TYPE_MISSING

        val purchase = if (type == TransactionType.REFUND) {
            entries.firstOrNull { it.id == input.refundOf && !it.isVoided && it.current.type == TransactionType.EXPENSE }
                .also { if (it == null) blockers += ProposalBlocker.PURCHASE_MISSING }
        } else {
            null
        }
        // A refund goes back to the purchase's own account, so that account decides.
        val accountId = purchase?.current?.accountId ?: input.accountId
        val account = accounts.firstOrNull { it.id == accountId }
        if (account == null && (type != TransactionType.REFUND || purchase != null)) blockers += ProposalBlocker.ACCOUNT_MISSING

        val amount = amountFor(input.amount, input.currency, account, blockers)
        val date = input.date
        when {
            date == null -> blockers += ProposalBlocker.DATE_MISSING
            date.isAfter(today) -> blockers += ProposalBlocker.DATE_IN_FUTURE
        }
        if (blockers.isNotEmpty() || type == null || account == null || amount == null || date == null) {
            return ProposalCheck(blockers, null)
        }

        val title = input.title.trim().take(MAX_TITLE).ifEmpty { null }
        val note = input.note.trim().take(MAX_NOTE).ifEmpty { null }
        val id = TransactionId(draft.recordIdFor(index))
        val transaction = when (type) {
            TransactionType.EXPENSE -> Transaction(
                id = id,
                accountId = account.id,
                type = type,
                amount = amount,
                date = date,
                reportingConversion = CurrencyConverter.toReporting(amount, null),
                spendingClass = input.spendingClass ?: SpendingClass.UNKNOWN,
                categoryId = ExpenseCategory.fromId(input.categoryId)?.id,
                merchant = title,
                note = note,
                source = RecordSource.QUICK_NOTE,
                createdAt = now,
            )
            TransactionType.INCOME -> Transaction(
                id = id,
                accountId = account.id,
                type = type,
                amount = amount,
                date = date,
                reportingConversion = CurrencyConverter.toReporting(amount, null),
                merchant = title,
                note = note,
                source = RecordSource.QUICK_NOTE,
                createdAt = now,
            )
            TransactionType.REFUND -> {
                val result = RefundRules.validate(
                    RefundRules.summary(purchase!!, entries),
                    account,
                    RefundFormInput(amount = input.amount, date = date, note = note.orEmpty()),
                    today,
                )
                when (result) {
                    is RefundFormResult.Invalid -> {
                        if (RefundProblem.AMOUNT_TOO_HIGH in result.problems) blockers += ProposalBlocker.REFUND_TOO_HIGH
                        if (RefundProblem.NOTHING_LEFT in result.problems) blockers += ProposalBlocker.PURCHASE_MISSING
                        if (blockers.isEmpty()) blockers += ProposalBlocker.AMOUNT_INVALID
                        return ProposalCheck(blockers, null)
                    }
                    is RefundFormResult.Valid -> RefundRules.newRefund(id, result.details, now).copy(source = RecordSource.QUICK_NOTE)
                }
            }
            // The shared draft contract only produces these three; anything else is added by hand.
            else -> return ProposalCheck(setOf(ProposalBlocker.TYPE_MISSING), null)
        }
        return ProposalCheck(emptySet(), ConfirmableRecord.Ledger(transaction, account))
    }

    private fun checkExpected(draft: Draft, index: Int, input: ProposalInput.Expected, accounts: List<Account>, now: Instant): ProposalCheck {
        val blockers = mutableSetOf<ProposalBlocker>()
        val direction = input.direction
        if (direction == null) blockers += ProposalBlocker.DIRECTION_MISSING
        // An account is optional for something expected; an unknown one is simply not used.
        val account = accounts.firstOrNull { it.id == input.accountId }
        val amount = amountFor(input.amount, input.currency, account, blockers)
        if (input.dueDate == null) blockers += ProposalBlocker.DATE_MISSING
        val title = input.title.trim().take(MAX_TITLE)
        if (title.isEmpty()) blockers += ProposalBlocker.TITLE_MISSING
        if (blockers.isNotEmpty() || direction == null || amount == null || input.dueDate == null) return ProposalCheck(blockers, null)
        val payment = ExpectedPayment(
            id = ExpectedPaymentId(draft.recordIdFor(index)),
            direction = direction,
            amount = amount,
            dueDate = input.dueDate,
            title = title,
            source = ExpectedSource.QuickNote,
            accountId = account?.id,
            createdAt = now,
        )
        return ProposalCheck(emptySet(), ConfirmableRecord.Expectation(payment))
    }

    private fun checkTransfer(draft: Draft, index: Int, input: ProposalInput.Transfer, accounts: List<Account>, today: LocalDate, now: Instant): ProposalCheck {
        val blockers = mutableSetOf<ProposalBlocker>()
        val from = accounts.firstOrNull { it.id == input.fromId }
        val to = accounts.firstOrNull { it.id == input.toId }
        if (from == null) blockers += ProposalBlocker.FROM_MISSING
        if (to == null) blockers += ProposalBlocker.TO_MISSING
        if (from != null && to != null && from.id == to.id) blockers += ProposalBlocker.SAME_ACCOUNT
        val sent = amountFor(input.amount, input.currency, from, blockers)
        val date = input.date
        when {
            date == null -> blockers += ProposalBlocker.DATE_MISSING
            date.isAfter(today) -> blockers += ProposalBlocker.DATE_IN_FUTURE
        }
        // Between accounts of one currency nothing is lost; otherwise the received amount must be stated.
        if (to != null && input.receivedCurrency != null && to.currency != input.receivedCurrency) {
            blockers += ProposalBlocker.CURRENCY_MISMATCH
        }
        val received = when {
            sent == null || from == null || to == null -> null
            from.currency == to.currency && input.received.isBlank() -> sent
            else -> Money.parse(input.received, to.currency)?.takeIf { it.isPositive }
                .also { if (it == null) blockers += ProposalBlocker.RECEIVED_MISSING }
        }
        if (from != null && to != null && from.currency == to.currency && received != null && sent != received) {
            // A difference needs a separately recorded fee; never invent that expense or drop the difference.
            blockers += ProposalBlocker.RECEIVED_MISMATCH
        }
        if (blockers.isNotEmpty() || from == null || to == null || sent == null || received == null || date == null) {
            return ProposalCheck(blockers, null)
        }
        val transfer = Transfer(
            id = TransferId(draft.recordIdFor(index)),
            fromAccountId = from.id,
            toAccountId = to.id,
            sent = sent,
            received = received,
            date = date,
            sentConversion = CurrencyConverter.toReporting(sent, null),
            receivedConversion = CurrencyConverter.toReporting(received, null),
            source = RecordSource.QUICK_NOTE,
            createdAt = now,
        )
        return ProposalCheck(emptySet(), ConfirmableRecord.Moved(transfer, from, to))
    }

    /**
     * Exact positive money in the account's currency, or the one the note stated when no account is chosen.
     * An account whose currency differs from the stated one is a blocker, never a silent conversion.
     */
    private fun amountFor(text: String, stated: Currency?, account: Account?, blockers: MutableSet<ProposalBlocker>): Money? {
        if (account != null && stated != null && account.currency != stated) blockers += ProposalBlocker.CURRENCY_MISMATCH
        if (text.isBlank()) {
            blockers += ProposalBlocker.AMOUNT_MISSING
            return null
        }
        val currency = account?.currency ?: stated ?: Currencies.REPORTING
        return Money.parse(text, currency)?.takeIf { it.isPositive }
            ?: null.also { blockers += ProposalBlocker.AMOUNT_INVALID }
    }
}
