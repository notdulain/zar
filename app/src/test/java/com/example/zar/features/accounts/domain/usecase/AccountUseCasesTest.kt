package com.example.zar.features.accounts.domain.usecase

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.cash
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.features.accounts.domain.model.AccountDetails
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountUseCasesTest {

    private class FakeAccounts(initial: List<Account> = emptyList()) : AccountRepository {
        val accounts = MutableStateFlow(initial)
        override fun observeAccounts(): Flow<List<Account>> = accounts
        override suspend fun saveAccount(account: Account) {
            accounts.value = accounts.value.filterNot { it.id == account.id } + account
        }
    }

    private class FixedLedger(entries: List<LedgerEntry>) : LedgerRepository by com.example.zar.core.domain.FakeLedgerRepository() {
        private val flow = MutableStateFlow(entries)
        override fun observeLedger(): Flow<List<LedgerEntry>> = flow
    }

    private val crypto = Account(
        AccountId("binance"), "Binance", AccountType.CRYPTO, Currencies.USDT, OpeningBalance.Unknown, LocalDate.of(2026, 10, 1),
    )

    private fun overview(accounts: List<Account>, entries: List<LedgerEntry> = emptyList()) = runBlocking {
        ObserveAccountsOverviewUseCase(FakeAccounts(accounts), FixedLedger(entries))().first()
    }

    @Test
    fun listsCashFirstThenBankThenCrypto() {
        val result = overview(listOf(crypto, bank, cash, payoneer))
        assertEquals(listOf("cash", "bank", "payoneer", "binance"), result.balances.map { it.account.id.value })
    }

    @Test
    fun totalAddsKnownReportingBalancesAndNeverCountsUnknownAsZero() {
        val result = overview(
            listOf(bank, cash, payoneer, crypto),
            listOf(entry(tx("e1", TransactionType.EXPENSE, lkr(500), cash))),
        )
        // bank 10,000 + cash (2,000 - 500); USD and unknown crypto stay out.
        assertEquals(lkr(11_500), result.total)
        assertEquals(2, result.countedAccounts)
        assertEquals(2, result.otherCurrencyAccounts)
        assertNull(result.balances.first { it.account.id == crypto.id }.current)
    }

    @Test
    fun archivedAccountsAreSeparatedAndOutOfTheTotal() {
        val result = overview(listOf(bank, cash.copy(isArchived = true)))
        assertEquals(listOf("bank"), result.balances.map { it.account.id.value })
        assertEquals(listOf("cash"), result.archived.map { it.account.id.value })
        assertEquals(lkr(10_000), result.total)
    }

    @Test
    fun noKnownBalancesMeansNoTotal() {
        val result = overview(listOf(crypto))
        assertNull(result.total)
        assertEquals(0, result.countedAccounts)
    }

    @Test
    fun openingBalancesAreNotReceivedIncome() = runBlocking {
        val accounts = FakeAccounts()
        val save = SaveAccountUseCase(accounts)
        val details = AccountDetails("Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Known(lkr(50_000)), LocalDate.of(2026, 10, 1))
        assertTrue(save(AccountId("n1"), details).isSuccess)
        val received = LedgerSummaryCalculator.summarize(emptyList(), YearMonth.of(2026, 10).let { it.atDay(1)..it.atEndOfMonth() }).received
        assertTrue(received.amount.isZero)
        assertEquals(lkr(50_000), overview(accounts.accounts.value).total)
    }

    @Test
    fun detailShowsLatestActiveEntriesOfThatAccountOnly() = runBlocking {
        val entries = listOf(
            entry(tx("old", TransactionType.EXPENSE, lkr(1), cash, LocalDate.of(2026, 10, 2))),
            entry(tx("a", TransactionType.EXPENSE, lkr(1), cash, LocalDate.of(2026, 10, 12))),
            entry(tx("b", TransactionType.EXPENSE, lkr(1), cash, LocalDate.of(2026, 10, 14))),
            entry(tx("c", TransactionType.INCOME, lkr(1), cash, LocalDate.of(2026, 10, 16))),
            entry(tx("voided", TransactionType.EXPENSE, lkr(1), cash, LocalDate.of(2026, 10, 18)), voided = true),
            entry(tx("other", TransactionType.EXPENSE, lkr(1), bank, LocalDate.of(2026, 10, 19))),
        )
        val detail = ObserveAccountDetailUseCase(FakeAccounts(listOf(bank, cash)), FixedLedger(entries))(cash.id).first()!!
        assertEquals(listOf("c", "b", "a"), detail.recent.map { it.id.value })
        assertNull(ObserveAccountDetailUseCase(FakeAccounts(listOf(bank)), FixedLedger(entries))(AccountId("missing")).first())
    }

    @Test
    fun savingTwiceWithTheSameIdKeepsOneAccount() = runBlocking {
        val accounts = FakeAccounts()
        val save = SaveAccountUseCase(accounts)
        val details = AccountDetails("Cash", AccountType.CASH, Currencies.LKR, OpeningBalance.Known(lkr(0)), LocalDate.of(2026, 10, 1))
        save(AccountId("same"), details)
        save(AccountId("same"), details)
        assertEquals(1, accounts.accounts.value.size)
    }

    @Test
    fun editingKeepsTheIdAndRejectsACurrencyChange() = runBlocking {
        val accounts = FakeAccounts(listOf(bank))
        val save = SaveAccountUseCase(accounts)
        val renamed = AccountDetails("Renamed", AccountType.BANK, Currencies.LKR, bank.openingBalance, bank.openingDate)
        assertTrue(save(bank.id, renamed, existing = bank).isSuccess)
        assertEquals("Renamed", accounts.accounts.value.single().name)

        val changedCurrency = renamed.copy(currency = Currencies.USD, openingBalance = OpeningBalance.Known(com.example.zar.core.domain.TestLedger.usd(1)))
        val failure = save(bank.id, changedCurrency, existing = bank).exceptionOrNull()
        assertTrue(failure is LedgerError.InvalidRecord)
        assertEquals(Currencies.LKR, accounts.accounts.value.single().currency)
    }

    @Test
    fun archiveAndRestoreKeepEverythingElse() = runBlocking {
        val accounts = FakeAccounts(listOf(bank))
        val setArchived = SetAccountArchivedUseCase(accounts)
        setArchived(bank, true)
        assertEquals(bank.copy(isArchived = true), accounts.accounts.value.single())
        setArchived(accounts.accounts.value.single(), false)
        assertEquals(bank, accounts.accounts.value.single())
    }

    @Test
    fun activeAccountsHideArchivedOnes() = runBlocking {
        val accounts = FakeAccounts(listOf(bank, cash.copy(isArchived = true), crypto))
        assertEquals(listOf("bank", "binance"), ObserveActiveAccountsUseCase(accounts)().first().map { it.id.value })
        assertEquals(crypto, ObserveAccountUseCase(accounts)(crypto.id).first())
    }

    @Test
    fun cryptoOpeningCanBeApproximateAndStillCounted() {
        val approx = crypto.copy(openingBalance = OpeningBalance.Approximate(CryptoAmount.of("100".toBigDecimal(), Currencies.USDT)))
        val balance = overview(listOf(approx)).balances.single()
        assertTrue(balance.isApproximate)
        assertEquals(CryptoAmount.of("100".toBigDecimal(), Currencies.USDT), balance.current)
    }
}
