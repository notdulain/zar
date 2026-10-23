package com.example.zar.features.crypto.domain.usecase

import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.repository.AccountRepository
import com.example.zar.core.domain.repository.LedgerRepository
import com.example.zar.core.domain.usecase.SaveTransferUseCase
import com.example.zar.features.crypto.domain.CryptoMovementRules
import com.example.zar.features.crypto.domain.CryptoPortfolioCalculator
import com.example.zar.features.crypto.domain.model.CryptoMovement
import com.example.zar.features.crypto.domain.model.CryptoPortfolio
import com.example.zar.features.crypto.domain.model.OpeningCost
import com.example.zar.features.crypto.domain.repository.OpeningCostRepository
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Holdings, movements and realized profit or loss. Recomputed whenever records change. */
class ObserveCryptoUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val ledger: LedgerRepository,
    private val openingCosts: OpeningCostRepository,
) {
    operator fun invoke(): Flow<CryptoPortfolio> = combine(
        accounts.observeAccounts(),
        ledger.observeLedger(),
        openingCosts.observeOpeningCosts(),
    ) { accountList, entries, costs -> CryptoPortfolioCalculator.calculate(accountList, entries, costs) }
}

/** Accounts a crypto movement can use: every account that isn't archived. */
class ObserveMovementAccountsUseCase @Inject constructor(
    private val accounts: AccountRepository,
) {
    operator fun invoke(): Flow<List<Account>> = accounts.observeAccounts().map { list -> list.filterNot { it.isArchived } }
}

/** Saves a crypto movement as a transfer between owned accounts. Reuse [id] when retrying. */
class RecordCryptoMovementUseCase @Inject constructor(
    private val saveTransfer: SaveTransferUseCase,
) {
    suspend operator fun invoke(id: TransferId, movement: CryptoMovement, createdAt: Instant): Result<Unit> {
        val transfer = runCatching { CryptoMovementRules.newTransfer(id, movement, createdAt) }
            .getOrElse { return Result.failure(it) }
        return saveTransfer(transfer, movement.from, movement.to)
    }
}

class SetOpeningCostUseCase @Inject constructor(
    private val repository: OpeningCostRepository,
) {
    /** Saves the wallet's opening cost, or clears it when [cost] is null. */
    suspend operator fun invoke(wallet: Account, cost: FiatMoney?, isApproximate: Boolean, now: Instant): Result<Unit> = try {
        require(cost == null || cost.currency == Currencies.REPORTING) { "Opening cost must be in LKR" }
        if (cost == null) repository.clearOpeningCost(wallet.id) else repository.saveOpeningCost(OpeningCost(wallet.id, cost, isApproximate, now))
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
