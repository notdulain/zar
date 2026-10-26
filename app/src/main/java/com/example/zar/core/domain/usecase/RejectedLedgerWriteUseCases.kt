package com.example.zar.core.domain.usecase

import com.example.zar.core.domain.model.RejectedLedgerWrite
import com.example.zar.core.domain.repository.LedgerRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Saves the server refused after the phone accepted them. Empty when everything saved was accepted. */
class ObserveRejectedLedgerWritesUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    operator fun invoke(): Flow<List<RejectedLedgerWrite>> = ledger.observeRejectedWrites()
}

/** Resubmits a refused save under its original IDs, so it cannot be duplicated. */
class RetryRejectedLedgerWriteUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> = resultOf { ledger.retryRejectedWrite(id) }
}

/** Gives up on a refused save so it is no longer listed. */
class DismissRejectedLedgerWriteUseCase @Inject constructor(
    private val ledger: LedgerRepository,
) {
    operator fun invoke(id: String) = ledger.dismissRejectedWrite(id)
}
