package com.example.zar.features.export

import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.TestLedger
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.TransactionType
import com.example.zar.features.export.domain.model.ExportKind
import com.example.zar.features.export.domain.usecase.BuildExportUseCase
import com.example.zar.features.export.domain.usecase.ObserveExportCountsUseCase
import com.example.zar.features.export.domain.usecase.SaveExportUseCase
import com.example.zar.features.export.presentation.viewmodel.ExportMessage
import com.example.zar.features.export.presentation.viewmodel.ExportViewModel
import com.example.zar.features.freelance.FakeExpectedPaymentRepository
import com.example.zar.features.freelance.FreelanceFixtures
import com.example.zar.features.home.StaticLedgerRepository
import com.example.zar.features.plans.FakePlanAccounts
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private val ledger = StaticLedgerRepository()
    private val expected = FakeExpectedPaymentRepository()
    private val accounts = FakePlanAccounts(listOf(TestLedger.bank))
    private val writer = RecordingWriter()
    private var session = FakeSession()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ExportViewModel(
        ObserveExportCountsUseCase(ledger, expected),
        BuildExportUseCase(session, ledger, expected, accounts),
        SaveExportUseCase(writer),
    )

    private fun withRecords() {
        ledger.entries.value = LedgerProjector.project(
            listOf(tx("a", TransactionType.EXPENSE, lkr(500)), tx("b", TransactionType.INCOME, lkr(900))),
            emptyList(), emptyList(),
        )
        expected.payments.value = listOf(FreelanceFixtures.milestone("m1", "p1", usd(1_000)))
    }

    @Test
    fun showsHowManyRowsEachExportHas() = runTest {
        withRecords()
        val state = viewModel().uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.counts!!.transactions)
        assertEquals(1, state.counts!!.expectedPayments)
    }

    @Test
    fun exportingBuildsTheFileAndWaitsForTheUserToPickAPlace() = runTest {
        withRecords()
        val vm = viewModel()
        vm.export(ExportKind.TRANSACTIONS)
        advanceUntilIdle()

        val pending = vm.uiState.value.pending!!
        assertEquals(ExportKind.TRANSACTIONS, pending.kind)
        assertEquals(2, pending.rowCount)
        assertTrue(writer.written.isEmpty())
    }

    @Test
    fun pickingAPlaceSavesTheFileAndConfirms() = runTest {
        withRecords()
        val vm = viewModel()
        vm.export(ExportKind.EXPECTED_PAYMENTS)
        advanceUntilIdle()
        vm.onDestinationPicked("content://docs/7")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.pending)
        assertNull(state.message)
        assertEquals("content://docs/7", writer.written.single().first)
        assertEquals(ExportKind.EXPECTED_PAYMENTS, state.saved!!.kind)
        assertFalse(state.isWorking)
    }

    @Test
    fun thePickerIsOpenedOnceEvenIfTheScreenIsRecreated() = runTest {
        withRecords()
        val vm = viewModel()
        vm.export(ExportKind.TRANSACTIONS)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.needsPicker)

        vm.onPickerOpened()
        // A rotation recomposes the screen with the same pending file; it must not reopen the picker.
        assertFalse(vm.uiState.value.needsPicker)
        assertNotNull(vm.uiState.value.pending)

        writer.gate = CompletableDeferred()
        vm.onDestinationPicked("content://docs/7")
        // While the file is being written the picker must not be asked for again.
        assertTrue(vm.uiState.value.isWorking)
        assertFalse(vm.uiState.value.needsPicker)

        writer.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.pickerOpen)
        assertFalse(vm.uiState.value.needsPicker)
        assertEquals(1, writer.written.size)
    }

    @Test
    fun theConfirmationUsesTheNameTheUserSavedUnder() = runTest {
        withRecords()
        writer.savedName = "march-backup.csv"
        val vm = viewModel()
        vm.export(ExportKind.TRANSACTIONS)
        advanceUntilIdle()
        vm.onDestinationPicked("content://docs/7")
        advanceUntilIdle()

        assertEquals("march-backup.csv", vm.uiState.value.saved!!.fileName)
    }

    @Test
    fun backingOutOfThePickerSavesNothingAndShowsNoError() = runTest {
        withRecords()
        val vm = viewModel()
        vm.export(ExportKind.TRANSACTIONS)
        advanceUntilIdle()
        vm.onDestinationPicked(null)

        assertNull(vm.uiState.value.pending)
        assertNull(vm.uiState.value.message)
        assertNull(vm.uiState.value.saved)
        assertTrue(writer.written.isEmpty())
    }

    @Test
    fun anEmptyExportExplainsInsteadOfOpeningThePicker() = runTest {
        val vm = viewModel()
        vm.export(ExportKind.TRANSACTIONS)
        advanceUntilIdle()

        assertEquals(ExportMessage.NOTHING_TO_EXPORT, vm.uiState.value.message)
        assertNull(vm.uiState.value.pending)
    }

    @Test
    fun aWriteFailureIsExplainedAndNothingIsClaimedSaved() = runTest {
        withRecords()
        writer.failing = true
        val vm = viewModel()
        vm.export(ExportKind.TRANSACTIONS)
        advanceUntilIdle()
        vm.onDestinationPicked("content://docs/7")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(ExportMessage.SAVE_FAILED, state.message)
        assertNull(state.saved)
        assertNull(state.pending)
        assertFalse(state.isWorking)
    }

    @Test
    fun aSignedOutUserGetsABuildFailureNotAnEmptyFile() = runTest {
        withRecords()
        val vm = viewModel()
        session = FakeSession(user = null)
        val signedOut = viewModel()
        signedOut.export(ExportKind.TRANSACTIONS)
        advanceUntilIdle()

        assertEquals(ExportMessage.BUILD_FAILED, signedOut.uiState.value.message)
        assertNull(signedOut.uiState.value.pending)
        assertNotNull(vm.uiState.value.counts)
    }

    @Test
    fun theNewestAttemptClearsAnOlderMessage() = runTest {
        withRecords()
        val vm = viewModel()
        writer.failing = true
        vm.export(ExportKind.TRANSACTIONS)
        advanceUntilIdle()
        vm.onDestinationPicked("content://docs/7")
        advanceUntilIdle()
        assertEquals(ExportMessage.SAVE_FAILED, vm.uiState.value.message)

        writer.failing = false
        vm.export(ExportKind.TRANSACTIONS)
        advanceUntilIdle()
        assertNull(vm.uiState.value.message)
        vm.onDestinationPicked("content://docs/8")
        advanceUntilIdle()
        assertEquals("content://docs/8", writer.written.single().first)
    }

    @Test
    fun aLoadFailureOffersRetry() = runTest {
        ledger.source = flow { throw IOException("cache unavailable") }
        val vm = viewModel()
        assertTrue(vm.uiState.value.loadFailed)

        ledger.source = null
        vm.retry()
        assertFalse(vm.uiState.value.loadFailed)
        assertNotNull(vm.uiState.value.counts)
    }
}
