package com.example.zar.features.home

import com.example.zar.core.domain.model.Correction
import com.example.zar.core.domain.model.EvidenceLink
import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.Transfer
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.features.allowance.FakeAllowanceRepository
import com.example.zar.features.allowance.domain.usecase.ObserveAllowanceUseCase
import com.example.zar.features.bills.domain.model.Bill
import com.example.zar.features.bills.domain.repository.BillRepository
import com.example.zar.features.bills.domain.usecase.ObserveBillsUseCase
import com.example.zar.features.bills.domain.usecase.ObserveNextBillUseCase
import com.example.zar.features.freelance.FakeExpectedPaymentRepository
import com.example.zar.features.freelance.domain.model.Project
import com.example.zar.features.freelance.domain.repository.ProjectRepository
import com.example.zar.features.freelance.domain.usecase.ObserveFreelanceUseCase
import com.example.zar.features.freelance.domain.usecase.ObserveOutstandingUseCase
import com.example.zar.features.home.domain.usecase.ObserveHomeCommitmentsUseCase
import com.example.zar.features.home.domain.usecase.ObserveHomeOverviewUseCase
import com.example.zar.features.plans.FakePlanAccounts
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Ledger whose entries a test sets directly; only reading is supported. */
class StaticLedgerRepository(initial: List<LedgerEntry> = emptyList()) : LedgerRepository {
    val entries = MutableStateFlow(initial)

    /** Replace to simulate a failing read, e.g. `flow { throw IOException() }`. */
    var source: Flow<List<LedgerEntry>>? = null

    override fun observeLedger(): Flow<List<LedgerEntry>> = source ?: entries

    override suspend fun saveTransaction(transaction: Transaction) = error("not used")
    override suspend fun saveTransfer(transfer: Transfer) = error("not used")
    override suspend fun addCorrection(correction: Correction) = error("not used")
    override suspend fun void(markers: List<VoidMarker>) = error("not used")
    override suspend fun linkEvidence(link: EvidenceLink) = error("not used")
}

internal fun emptyLedger(): Flow<List<LedgerEntry>> = flowOf(emptyList())

/** Projects that can be made to fail, to check one broken source never blanks Home. */
class SwitchableProjects : ProjectRepository {
    val projects = MutableStateFlow<List<Project>>(emptyList())
    var failing = false

    override fun observeProjects(): Flow<List<Project>> =
        if (failing) flow { throw IOException("projects unavailable") } else projects

    override suspend fun saveProject(project: Project) = error("not used")
}

class SwitchableBills : BillRepository {
    val bills = MutableStateFlow<List<Bill>>(emptyList())

    override fun observeBills(): Flow<List<Bill>> = bills

    override suspend fun saveBill(bill: Bill) = error("not used")
}

/** The real Home use cases wired to in-memory repositories. */
class HomeRig {
    val ledger = StaticLedgerRepository()
    val projects = SwitchableProjects()
    val expected = FakeExpectedPaymentRepository()
    val bills = SwitchableBills()
    private val accounts = FakePlanAccounts(emptyList())
    val allowanceSettings = FakeAllowanceRepository()

    val overview = ObserveHomeOverviewUseCase(ledger)
    val allowance = ObserveAllowanceUseCase(allowanceSettings, ledger, expected)
    val commitments = ObserveHomeCommitmentsUseCase(
        ObserveOutstandingUseCase(ObserveFreelanceUseCase(projects, expected, ledger)),
        ObserveNextBillUseCase(ObserveBillsUseCase(bills, accounts, expected, ledger)),
    )
}
