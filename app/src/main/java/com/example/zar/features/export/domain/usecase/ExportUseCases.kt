package com.example.zar.features.export.domain.usecase

import com.example.zar.core.domain.calculation.ZarTime
import com.example.zar.core.domain.model.LedgerError
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.ExpectedPaymentRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.repository.UserSessionProvider
import com.example.zar.core.domain.usecase.resultOf
import com.example.zar.features.export.domain.ExpectedPaymentsCsv
import com.example.zar.features.export.domain.TransactionsCsv
import com.example.zar.features.export.domain.model.ExportCounts
import com.example.zar.features.export.domain.model.ExportFile
import com.example.zar.features.export.domain.model.ExportKind
import com.example.zar.features.export.domain.repository.ExportFileWriter
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** How many rows each export would hold. Follows saved records, including offline ones. */
class ObserveExportCountsUseCase @Inject constructor(
    private val ledger: LedgerRepository,
    private val expected: ExpectedPaymentRepository,
) {
    operator fun invoke(): Flow<ExportCounts> = combine(
        ledger.observeLedger(),
        expected.observeExpectedPayments(),
    ) { entries, payments -> ExportCounts(entries.size, payments.size) }
}

/**
 * Builds one export from the signed-in user's records. Every repository is already scoped to the
 * current user; the sign-in check here makes a signed-out export an explicit failure rather than
 * an empty file.
 */
class BuildExportUseCase @Inject constructor(
    private val session: UserSessionProvider,
    private val ledger: LedgerRepository,
    private val expected: ExpectedPaymentRepository,
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke(kind: ExportKind, today: LocalDate = ZarTime.today()): Result<ExportFile> = resultOf {
        session.currentUserId() ?: throw LedgerError.NotSignedIn()
        val entries = ledger.observeLedger().first()
        val accountList = accounts.observeAccounts().first()
        when (kind) {
            ExportKind.TRANSACTIONS -> {
                val csv = TransactionsCsv.build(entries, accountList)
                ExportFile(kind, "zar-transactions-$today.csv", csv.text, csv.rowCount)
            }
            ExportKind.EXPECTED_PAYMENTS -> {
                val csv = ExpectedPaymentsCsv.build(
                    payments = expected.observeExpectedPayments().first(),
                    matches = expected.observeMatches().first(),
                    entries = entries,
                    accounts = accountList,
                    today = today,
                )
                ExportFile(kind, "zar-expected-payments-$today.csv", csv.text, csv.rowCount)
            }
        }
    }
}

/** Writes a finished export to the destination the user picked. */
class SaveExportUseCase @Inject constructor(
    private val writer: ExportFileWriter,
) {
    suspend operator fun invoke(location: String, file: ExportFile): Result<Unit> = resultOf {
        writer.write(location, file)
    }
}
