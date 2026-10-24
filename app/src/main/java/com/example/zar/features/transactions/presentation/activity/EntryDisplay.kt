package com.example.zar.features.transactions.presentation.activity

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.zar.R
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.Direction
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.transactions.domain.manual.model.ExpenseCategory
import com.example.zar.features.transactions.presentation.income.IncomeFormat
import com.example.zar.features.transactions.presentation.manual.screen.labelRes
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Words, icons and number formats for an entry, shared by the Activity list, detail and correction screens. */
internal object EntryDisplay {
    private val DAY = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)
    private val STAMP = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** "Thursday, 24 Oct". */
    fun day(date: LocalDate): String = date.format(DAY)

    fun time(time: LocalTime): String = time.format(TIME)

    /** "25 Oct, 9:10 AM" in Sri Lanka time, for when a correction was made. */
    fun stamp(at: Instant): String = at.atZone(ZarTime.ZONE).format(STAMP)

    fun dayMonth(date: LocalDate): String = date.format(DAY_MONTH)

    /** "8,400" for LKR, "USD 40" for anything else. */
    fun money(money: Money): String =
        if (money.currency == Currencies.REPORTING) IncomeFormat.amount(money) else IncomeFormat.withCode(money)

    /** "− 8,400" or "+ 125,000", signed by the direction of the money. */
    fun signed(tx: Transaction): String = "${if (tx.direction == Direction.IN) "+" else "−"} ${money(tx.amount)}"

    /** Money coming in (income, a refund or a transfer in) is tinted teal; money going out is plain. */
    fun isInflow(tx: Transaction): Boolean = tx.direction == Direction.IN

    @StringRes
    fun typeLabel(type: TransactionType): Int = when (type) {
        TransactionType.INCOME -> R.string.home_activity_income
        TransactionType.EXPENSE -> R.string.home_activity_expense
        TransactionType.TRANSFER_IN -> R.string.home_activity_transfer_in
        TransactionType.TRANSFER_OUT -> R.string.home_activity_transfer_out
        TransactionType.REFUND -> R.string.home_activity_refund
        TransactionType.ADJUSTMENT_IN -> R.string.home_activity_adjustment_in
        TransactionType.ADJUSTMENT_OUT -> R.string.home_activity_adjustment_out
    }

    /** The label for a saved category ID, or null for one this app does not know (for example from an import). */
    @StringRes
    fun categoryLabel(id: String?): Int? = ExpenseCategory.fromId(id)?.labelRes() ?: when (id) {
        "transfer-fee" -> R.string.activity_category_transfer_fee
        "salary" -> R.string.income_category_salary
        "freelance" -> R.string.income_category_freelance
        "allowance" -> R.string.income_category_allowance
        "gift" -> R.string.income_category_gift
        else -> null
    }

    @DrawableRes
    fun icon(tx: Transaction): Int = when (tx.type) {
        TransactionType.TRANSFER_OUT -> R.drawable.ic_ph_arrowupright
        TransactionType.TRANSFER_IN, TransactionType.REFUND -> R.drawable.ic_ph_arrow_down_left
        TransactionType.INCOME -> if (tx.categoryId == "freelance") R.drawable.ic_ph_briefcase else R.drawable.ic_ph_wallet
        TransactionType.ADJUSTMENT_IN, TransactionType.ADJUSTMENT_OUT -> R.drawable.ic_ph_receipt
        TransactionType.EXPENSE -> when (tx.categoryId) {
            "coffee-dining" -> R.drawable.ic_ph_fork_knife
            "groceries" -> R.drawable.ic_ph_basket
            "transport" -> R.drawable.ic_ph_car
            "bills" -> R.drawable.ic_ph_lightning
            "rent" -> R.drawable.ic_ph_house
            "subscriptions" -> R.drawable.ic_ph_television
            "transfer-fee" -> R.drawable.ic_ph_arrows_left_right
            else -> R.drawable.ic_ph_receipt
        }
    }
}

/**
 * The title a person gave the entry, or else what kind of entry it is. A refund reads "Refund · Keells Super" and a
 * balance adjustment says what was checked, so neither can be mistaken for a purchase or income.
 */
@Composable
internal fun entryTitle(tx: Transaction, accountType: AccountType? = null): String {
    val title = tx.merchant?.takeIf { it.isNotBlank() }
    return when (tx.type) {
        TransactionType.REFUND ->
            title?.let { stringResource(R.string.activity_refund_title_of, it) } ?: stringResource(EntryDisplay.typeLabel(tx.type))
        TransactionType.ADJUSTMENT_IN, TransactionType.ADJUSTMENT_OUT ->
            stringResource(if (accountType == AccountType.CASH) R.string.activity_adjustment_title_cash else R.string.activity_adjustment_title_other)
        else -> title ?: stringResource(EntryDisplay.typeLabel(tx.type))
    }
}

/** "Groceries · Oct 24 · Cash": the category (when known) or kind of entry, the day and the account. */
@Composable
internal fun entrySubtitle(tx: Transaction, accountName: String?): String {
    val category = EntryDisplay.categoryLabel(tx.categoryId)?.let { stringResource(it) }
    val kind = when (tx.type) {
        TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT -> stringResource(R.string.activity_kind_transfer)
        TransactionType.REFUND -> stringResource(R.string.activity_kind_refund)
        TransactionType.ADJUSTMENT_IN, TransactionType.ADJUSTMENT_OUT -> stringResource(R.string.activity_kind_adjustment)
        else -> null
    }
    return listOfNotNull(category ?: kind, IncomeFormat.shortDate(tx.date), accountName).joinToString(" · ")
}
