package com.example.zar.core.domain.usecase

import com.example.zar.core.domain.model.LedgerEntry
import com.example.zar.core.domain.repository.LedgerRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveLedgerUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    operator fun invoke(): Flow<List<LedgerEntry>> = ledger.observeLedger()
}
