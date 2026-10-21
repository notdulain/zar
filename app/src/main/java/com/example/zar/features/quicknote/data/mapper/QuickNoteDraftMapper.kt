package com.example.zar.features.quicknote.data.mapper

import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.Draft
import com.example.zar.core.domain.model.DraftField
import com.example.zar.core.domain.model.DraftId
import com.example.zar.core.domain.model.DraftProposal
import com.example.zar.core.domain.model.DraftSource
import com.example.zar.core.domain.model.DraftStatus
import com.example.zar.core.domain.model.SpendingClass
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.quicknote.domain.model.QuickNoteAccountContext
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftError
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftRequest
import com.example.zar.features.quicknote.domain.model.QuickNoteLimits
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate

/**
 * Turns the service's answer into a pending [Draft]. The answer is untrusted: the top-level shape must be right
 * (otherwise [QuickNoteDraftError.InvalidResponse]), but every field inside a proposal is checked on its own and
 * becomes missing when it is wrong. Nothing is guessed to fill a gap.
 *
 * Rules: unknown accounts are dropped; money is parsed exactly and must be positive and in its account's
 * currency; spending class applies to expenses only; an amount not written in the note is flagged uncertain;
 * a cross-currency transfer never gets a received amount unless the service gave one.
 */
internal object QuickNoteDraftMapper {

    private val AMOUNT = Regex("""\d{1,20}(\.\d{1,18})?""")
    private val CATEGORY = Regex("""[a-z0-9][a-z0-9 _-]{0,39}""")
    private val EARLIEST_DATE = LocalDate.of(2000, 1, 1)
    private const val MAX_DUE_YEARS_AHEAD = 5L

    fun toDraft(raw: Any?, request: QuickNoteDraftRequest, id: DraftId, createdAt: Instant): Draft {
        val proposals = (raw as? Map<*, *>)?.get("proposals") as? List<*> ?: throw QuickNoteDraftError.InvalidResponse()
        if (proposals.size > QuickNoteLimits.MAX_PROPOSALS) throw QuickNoteDraftError.InvalidResponse()
        val context = Context(request)
        return Draft(
            id = id,
            source = DraftSource.QUICK_NOTE,
            proposals = proposals.map { context.proposal(it) },
            status = DraftStatus.PENDING_REVIEW,
            createdAt = createdAt,
        )
    }

    private class Context(val request: QuickNoteDraftRequest) {
        val accounts: Map<String, QuickNoteAccountContext> = request.accounts.associateBy { it.id.value }

        fun proposal(raw: Any?): DraftProposal {
            val map = raw as? Map<*, *> ?: throw QuickNoteDraftError.InvalidResponse()
            return when (map["kind"]) {
                "ACTUAL_TRANSACTION" -> actual(map)
                "EXPECTED" -> expected(map)
                "TRANSFER" -> transfer(map)
                else -> throw QuickNoteDraftError.InvalidResponse()
            }
        }

        private fun actual(map: Map<*, *>): DraftProposal.ActualTransaction {
            val uncertain = uncertain(map).toMutableSet()
            val type = enumOrNull<TransactionType>(map["transactionType"])?.takeIf {
                it == TransactionType.INCOME || it == TransactionType.EXPENSE || it == TransactionType.REFUND
            }
            val account = account(map["accountId"])
            val amount = amount(map["amount"], map["currency"], account, uncertain)
            val spendingClass = if (type == TransactionType.EXPENSE) {
                // The ledger requires a class on every expense; UNKNOWN is the explicit "not classified".
                enumOrNull<SpendingClass>(map["spendingClass"]) ?: SpendingClass.UNKNOWN
            } else {
                null
            }
            return DraftProposal.ActualTransaction(
                type = type,
                amount = amount,
                accountId = account?.id,
                date = pastDate(map["date"]),
                spendingClass = spendingClass,
                categoryId = category(map["category"]),
                merchant = text(map["merchant"], 80),
                note = text(map["note"], 300),
                uncertainFields = uncertain,
            )
        }

        private fun expected(map: Map<*, *>): DraftProposal.Expected {
            val uncertain = uncertain(map).toMutableSet()
            val account = account(map["accountId"])
            return DraftProposal.Expected(
                direction = enumOrNull<Direction>(map["direction"]),
                amount = amount(map["amount"], map["currency"], account, uncertain),
                dueDate = dueDate(map["dueDate"]),
                title = text(map["title"], 80),
                accountId = account?.id,
                uncertainFields = uncertain,
            )
        }

        private fun transfer(map: Map<*, *>): DraftProposal.TransferBetweenAccounts {
            val uncertain = uncertain(map).toMutableSet()
            var from = account(map["accountId"])
            var to = account(map["toAccountId"])
            // Money cannot move from an account to itself, and we cannot tell which side the model got wrong.
            if (from != null && from.id == to?.id) {
                from = null
                to = null
            }
            val sent = amount(map["amount"], map["currency"], from, uncertain)
            val received = receivedAmount(map, to, sent, uncertain)
            return DraftProposal.TransferBetweenAccounts(
                fromAccountId = from?.id,
                toAccountId = to?.id,
                sent = sent,
                received = received,
                date = pastDate(map["date"]),
                uncertainFields = uncertain,
            )
        }

        /**
         * A stated received amount is parsed like any other. With none stated, the received side equals the sent
         * side only when the destination account is known and holds the same currency; otherwise it stays missing.
         */
        private fun receivedAmount(map: Map<*, *>, to: QuickNoteAccountContext?, sent: Money?, uncertain: MutableSet<DraftField>): Money? {
            val stated = map["receivedAmount"]
            if (stated != null) {
                return amount(stated, map["receivedCurrency"], to, uncertain)
            }
            return sent?.takeIf { to != null && to.currency == it.currency }
        }

        private fun account(raw: Any?): QuickNoteAccountContext? = (raw as? String)?.let { accounts[it] }

        /**
         * Exact positive money. The currency comes from the answer, else from the known account; it must match a
         * known account's currency. A bad value is missing. An amount not found in the note is flagged uncertain.
         */
        private fun amount(rawAmount: Any?, rawCurrency: Any?, account: QuickNoteAccountContext?, uncertain: MutableSet<DraftField>): Money? {
            val text = (rawAmount as? String)?.trim()
            if (text == null || !AMOUNT.matches(text)) return null
            val currency = (rawCurrency as? String)?.let(Currencies::find) ?: account?.currency ?: return null
            if (account != null && account.currency != currency) return null
            val money = Money.parse(text, currency)?.takeIf { it.isPositive } ?: return null
            if (!AmountGrounding.isGrounded(money.toDecimal(), request.note)) uncertain.add(DraftField.AMOUNT)
            return money
        }

        private fun pastDate(raw: Any?): LocalDate? =
            parseDate(raw)?.takeIf { it >= EARLIEST_DATE && it <= request.today }

        private fun dueDate(raw: Any?): LocalDate? =
            parseDate(raw)?.takeIf { it >= EARLIEST_DATE && it <= request.today.plusYears(MAX_DUE_YEARS_AHEAD) }

        private fun parseDate(raw: Any?): LocalDate? = try {
            (raw as? String)?.takeIf { it.length == 10 }?.let(LocalDate::parse)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun uncertain(map: Map<*, *>): Set<DraftField> =
        (map["uncertainFields"] as? List<*>).orEmpty().mapNotNull { enumOrNull<DraftField>(it) }.toSet()

    private inline fun <reified E : Enum<E>> enumOrNull(raw: Any?): E? =
        (raw as? String)?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

    /** Trimmed text without control characters; blank becomes missing. */
    private fun text(raw: Any?, limit: Int): String? =
        (raw as? String)?.filterNot { it.isISOControl() }?.trim()?.take(limit)?.takeIf { it.isNotEmpty() }

    private fun category(raw: Any?): String? =
        (raw as? String)?.trim()?.lowercase()?.takeIf { CATEGORY.matches(it) }
}
