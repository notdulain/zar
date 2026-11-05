package com.example.zar.features.transactions.presentation.income

import androidx.lifecycle.SavedStateHandle
import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.ExchangeRate
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.RateSource
import com.example.zar.core.domain.model.money.ReportingConversion
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.usecase.CorrectTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.VoidTransactionUseCase
import com.example.zar.features.rates.FakeExchangeRateRepository
import com.example.zar.features.rates.domain.FetchTodaysRateUseCase
import com.example.zar.features.rates.domain.RateFetch
import com.example.zar.features.rates.domain.RateFetchError
import com.example.zar.features.rates.domain.RateHint
import com.example.zar.features.rates.domain.RateQuote
import com.example.zar.features.transactions.domain.income.model.IncomeProblem
import com.example.zar.features.transactions.domain.income.usecase.EditIncomeUseCase
import com.example.zar.features.transactions.domain.income.usecase.ObserveIncomeAccountsUseCase
import com.example.zar.features.transactions.domain.income.usecase.ObserveIncomeEntryUseCase
import com.example.zar.features.transactions.domain.income.usecase.RecordIncomeUseCase
import com.example.zar.features.transactions.presentation.income.viewmodel.IncomeFormViewModel
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class RateAccounts(initial: List<Account>) : AccountRepository {
    val accounts = MutableStateFlow(initial)
    override fun observeAccounts(): Flow<List<Account>> = accounts
    override suspend fun saveAccount(account: Account) = Unit
}

/** The rate field of the income form: today's rate, typing over it, earlier dates, and editing a saved rate (issue #46). */
@OptIn(ExperimentalCoroutinesApi::class)
class IncomeFormViewModelRateTest {
    private val accounts = RateAccounts(listOf(bank, payoneer))
    private val ledger = FakeLedgerRepository()
    private val rateSource = FakeExchangeRateRepository()
    private val published = Instant.parse("2026-11-04T00:02:31Z")
    private val quote = RateQuote(Currencies.USD, Currencies.LKR, "332.444906".toBigDecimal(), published, Instant.parse("2026-11-05T00:18:21Z"))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = IncomeFormViewModel(
        handle,
        observeAccounts = ObserveIncomeAccountsUseCase(accounts),
        observeEntry = ObserveIncomeEntryUseCase(ledger),
        recordIncome = RecordIncomeUseCase(SaveTransactionUseCase(ledger)),
        editIncome = EditIncomeUseCase(CorrectTransactionUseCase(ledger, accounts)),
        voidTransaction = VoidTransactionUseCase(ledger),
        fetchTodaysRate = FetchTodaysRateUseCase(rateSource),
    )

    private fun usdIncome(): IncomeFormViewModel = viewModel().also {
        it.onAccountSelect(payoneer.id)
        it.onAmountChange("400")
    }

    private fun savedConversion(vm: IncomeFormViewModel): ReportingConversion {
        vm.save()
        return ledger.transactions.values.single().reportingConversion
    }

    @Test
    fun todaysRateFillsTheFieldAndIsSavedAsTheProvidersRate() {
        rateSource.answer = Result.success(quote)
        val vm = usdIncome()
        assertTrue(vm.uiState.value.canUseTodaysRate)

        vm.onUseTodaysRate()
        assertEquals("332.4449", vm.uiState.value.rate)
        assertEquals(RateHint.FILLED, vm.uiState.value.rateEntry.hint(vm.uiState.value.canUseTodaysRate))

        val conversion = savedConversion(vm) as ReportingConversion.Converted
        assertEquals(RateSource.PROVIDER, conversion.rate.source)
        assertEquals(published, conversion.rate.asOf)
        // USD 400 at 332.4449 is LKR 132,977.96.
        assertEquals(FiatMoney(13_297_796, Currencies.LKR), conversion.amount)
    }

    @Test
    fun aNumberTypedOverTodaysRateIsSavedAsTheUsersOwn() {
        rateSource.answer = Result.success(quote)
        val vm = usdIncome()
        vm.onUseTodaysRate()
        vm.onRateChange("330")

        val conversion = savedConversion(vm) as ReportingConversion.Converted
        assertEquals(RateSource.MANUAL, conversion.rate.source)
        assertEquals(FiatMoney(13_200_000, Currencies.LKR), conversion.amount)
    }

    @Test
    fun aFailedFetchShowsAMessageAndSavingStillWorks() {
        rateSource.answer = Result.failure(RateFetchError.RateLimited())
        val vm = usdIncome()
        vm.onUseTodaysRate()

        assertEquals(RateFetch.FAILED, vm.uiState.value.rateEntry.fetch)
        assertEquals(RateHint.FAILED, vm.uiState.value.rateEntry.hint(vm.uiState.value.canUseTodaysRate))
        assertEquals(ReportingConversion.Missing(Currencies.LKR), savedConversion(vm))
    }

    @Test
    fun anEarlierDateOffersNoTodaysRateAndTakesAFetchedRateOut() {
        rateSource.answer = Result.success(quote)
        val vm = usdIncome()
        vm.onUseTodaysRate()
        vm.onDateSelect(ZarTime.today().minusDays(1))

        assertEquals("", vm.uiState.value.rate)
        assertFalse(vm.uiState.value.canUseTodaysRate)
        assertEquals(RateHint.EARLIER_DATE, vm.uiState.value.rateEntry.hint(vm.uiState.value.canUseTodaysRate))

        rateSource.requests.clear()
        vm.onUseTodaysRate()
        assertTrue(rateSource.requests.isEmpty())
    }

    @Test
    fun anAnswerThatArrivesAfterTheAccountChangedIsIgnored() {
        rateSource.answer = Result.success(quote)
        rateSource.gate = CompletableDeferred()
        val vm = usdIncome()
        vm.onUseTodaysRate()
        assertEquals(RateFetch.FETCHING, vm.uiState.value.rateEntry.fetch)

        vm.onAccountSelect(bank.id)
        rateSource.gate!!.complete(Unit)

        assertEquals("", vm.uiState.value.rate)
        assertEquals(RateFetch.IDLE, vm.uiState.value.rateEntry.fetch)
    }

    @Test
    fun anAnswerThatArrivesAfterTheUserTypedARateDoesNotReplaceIt() {
        rateSource.answer = Result.success(quote)
        rateSource.gate = CompletableDeferred()
        val vm = usdIncome()
        vm.onUseTodaysRate()
        assertEquals(RateFetch.FETCHING, vm.uiState.value.rateEntry.fetch)

        vm.onRateChange("330")
        rateSource.gate!!.complete(Unit)

        assertEquals("330", vm.uiState.value.rate)
        assertEquals(RateFetch.IDLE, vm.uiState.value.rateEntry.fetch)
        assertEquals(RateSource.MANUAL, savedConversion(vm).let { (it as ReportingConversion.Converted).rate.source })
    }

    @Test
    fun aLkrAccountHasNoTodaysRate() {
        val vm = viewModel()
        vm.onAccountSelect(bank.id)
        assertFalse(vm.uiState.value.needsRate)
        assertFalse(vm.uiState.value.canUseTodaysRate)
    }

    @Test
    fun aValidFetchClearsAnEarlierRateError() {
        rateSource.answer = Result.success(quote)
        val vm = usdIncome()
        vm.onRateChange("abc")
        vm.save()
        assertTrue(IncomeProblem.RATE_INVALID in vm.uiState.value.problems)

        vm.onUseTodaysRate()
        assertFalse(IncomeProblem.RATE_INVALID in vm.uiState.value.problems)
    }

    // --- Editing a saved income --------------------------------------------------------------------------------

    private fun savedIncome(source: RateSource): String {
        val conversion = ReportingConversion.Converted(
            ExchangeRate(Currencies.USD, Currencies.LKR, "332.4449".toBigDecimal(), published, source), FiatMoney(13_297_796, Currencies.LKR),
        )
        val tx = TestLedger.tx("inc1", TransactionType.INCOME, usd(400), account = payoneer, conversion = conversion)
        runBlocking { ledger.saveTransaction(tx) }
        return "inc1"
    }

    @Test
    fun aSavedProviderRateIsShownWithItsSourceAndTimeAndKeptWhenNothingChanges() {
        val vm = viewModel(SavedStateHandle(mapOf("incomeId" to savedIncome(RateSource.PROVIDER))))
        val entry = vm.uiState.value.rateEntry

        assertEquals("332.4449", vm.uiState.value.rate)
        assertEquals(RateHint.SAVED_FROM_PROVIDER, entry.hint(vm.uiState.value.canUseTodaysRate))
        assertEquals(published, entry.savedFromProvider?.asOf)
        // Saved on 10 Oct, so today's rate is not offered for it.
        assertFalse(vm.uiState.value.canUseTodaysRate)

        vm.onNoteChange("same rate, new note")
        vm.save()
        val change = ledger.corrections.single().changes
        assertEquals("no rate change when the number is untouched", null, change.reportingConversion)
    }

    @Test
    fun changingASavedProviderRateReplacesItWithTheUsersOwn() {
        val vm = viewModel(SavedStateHandle(mapOf("incomeId" to savedIncome(RateSource.PROVIDER))))
        vm.onRateChange("331")
        vm.save()

        val conversion = ledger.corrections.single().changes.reportingConversion as ReportingConversion.Converted
        assertEquals(RateSource.MANUAL, conversion.rate.source)
        assertEquals(0, "331".toBigDecimal().compareTo(conversion.rate.rate))
    }
}
