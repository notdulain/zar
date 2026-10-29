package com.example.zar.features.crypto

import androidx.lifecycle.viewModelScope
import com.example.zar.core.domain.FakeAccountRepository
import com.example.zar.core.domain.FakeLedgerRepository
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.SaveTransferUseCase
import com.example.zar.core.domain.usecase.VoidTransactionUseCase
import com.example.zar.features.crypto.domain.CryptoProblem
import com.example.zar.features.crypto.domain.model.OpeningCost
import com.example.zar.features.crypto.domain.repository.OpeningCostRepository
import com.example.zar.features.crypto.domain.usecase.ObserveCryptoUseCase
import com.example.zar.features.crypto.domain.usecase.ObserveMovementAccountsUseCase
import com.example.zar.features.crypto.domain.usecase.RecordCryptoMovementUseCase
import com.example.zar.features.crypto.domain.usecase.SetOpeningCostUseCase
import com.example.zar.features.crypto.presentation.viewmodel.CryptoMessage
import com.example.zar.features.crypto.presentation.viewmodel.CryptoMovementViewModel
import com.example.zar.features.crypto.presentation.viewmodel.CryptoViewModel
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** L10: Crypto screens when empty, when loading fails, and when saving fails offline. */
@OptIn(ExperimentalCoroutinesApi::class)
class CryptoViewModelsTest {

    private val wallet = Account(AccountId("binance-btc"), "Binance BTC", AccountType.CRYPTO, Currencies.BTC,
        OpeningBalance.Known(CryptoAmount.of("0.02".toBigDecimal(), Currencies.BTC)), LocalDate.of(2026, 10, 1))

    /** The shared fake ledger, with transfers that fail while "offline". */
    private class Ledger(val inner: FakeLedgerRepository = FakeLedgerRepository()) : LedgerRepository by inner {
        var offline = false
        override suspend fun saveTransfer(transfer: Transfer) {
            if (offline) throw IOException("Offline")
            inner.saveTransfer(transfer)
        }
    }

    private class Costs : OpeningCostRepository {
        val costs = MutableStateFlow<Map<AccountId, OpeningCost>>(emptyMap())
        var offline = false
        override fun observeOpeningCosts(): Flow<Map<AccountId, OpeningCost>> = costs
        override suspend fun saveOpeningCost(cost: OpeningCost) {
            if (offline) throw IOException("Offline")
            costs.value = costs.value + (cost.accountId to cost)
        }
        override suspend fun clearOpeningCost(accountId: AccountId) { costs.value = costs.value - accountId }
    }

    private class BrokenAccounts : AccountRepository {
        override fun observeAccounts() = flow<List<Account>> { throw IOException("Permission denied") }
        override suspend fun saveAccount(account: Account) = Unit
    }

    private val ledger = Ledger()
    private val costs = Costs()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun overview(accounts: AccountRepository = FakeAccountRepository(bank, wallet)) =
        CryptoViewModel(ObserveCryptoUseCase(accounts, ledger, costs), SetOpeningCostUseCase(costs), VoidTransactionUseCase(ledger))

    @Test
    fun overviewLoadsAWalletWithNoMovements() = runTest {
        val vm = overview()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertNotNull(state.portfolio)
        assertTrue("Nothing moved yet", state.portfolio!!.movements.isEmpty())
        vm.viewModelScope.cancel()
    }

    @Test
    fun overviewSaysLoadingFailed() = runTest {
        val vm = overview(BrokenAccounts())
        assertTrue(vm.uiState.value.loadFailed)
        assertNull(vm.uiState.value.portfolio)
        vm.viewModelScope.cancel()
    }

    @Test
    fun openingCostRejectsBadInputAndStaysOpenWhenSavingFails() = runTest {
        val vm = overview()
        vm.editCost(wallet)
        vm.onCostChange("abc")
        vm.saveCost()
        assertTrue(vm.uiState.value.costInvalid)

        vm.onCostChange("640000")
        costs.offline = true
        vm.saveCost()
        assertEquals(CryptoMessage.FAILED, vm.uiState.value.message)
        assertEquals("The sheet stays open to retry", wallet, vm.uiState.value.editingCostFor)
        assertTrue(costs.costs.value.isEmpty())

        costs.offline = false
        vm.saveCost()
        assertNull(vm.uiState.value.message)
        assertNull(vm.uiState.value.editingCostFor)
        assertEquals(wallet.id, costs.costs.value.keys.single())
        vm.viewModelScope.cancel()
    }

    private fun movement() = CryptoMovementViewModel(
        ObserveMovementAccountsUseCase(FakeAccountRepository(bank, wallet)),
        RecordCryptoMovementUseCase(SaveTransferUseCase(ledger)),
    )

    @Test
    fun movementNeedsBothAccountsAndAmounts() = runTest {
        val vm = movement()
        vm.save()
        assertTrue(CryptoProblem.FROM_MISSING in vm.uiState.value.problems)
        assertTrue(CryptoProblem.TO_MISSING in vm.uiState.value.problems)
        assertTrue(ledger.inner.transactions.isEmpty())
        vm.viewModelScope.cancel()
    }

    @Test
    fun failedBuyKeepsTheFormAndRetryRecordsItOnce() = runTest {
        val vm = movement()
        vm.onFromSelect(bank.id)
        vm.onToSelect(wallet.id)
        vm.onSentChange("50000")
        vm.onReceivedChange("0.0025")
        ledger.offline = true
        vm.save()
        assertEquals(CryptoMessage.FAILED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isFinished)
        assertEquals("0.0025", vm.uiState.value.received)

        ledger.offline = false
        vm.save()
        assertTrue(vm.uiState.value.isFinished)
        assertEquals("One transfer: out of the bank and into the wallet", 2, ledger.inner.transactions.size)
        vm.save()
        assertEquals("A second tap saves nothing more", 2, ledger.inner.transactions.size)
        vm.viewModelScope.cancel()
    }
}
