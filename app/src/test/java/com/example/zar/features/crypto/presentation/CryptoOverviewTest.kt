package com.example.zar.features.crypto.presentation

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.crypto.domain.CryptoMovementRules
import com.example.zar.features.crypto.domain.CryptoPortfolioCalculator
import com.example.zar.features.crypto.domain.model.CryptoMovement
import com.example.zar.features.crypto.domain.model.CryptoMovementKind
import com.example.zar.features.crypto.domain.model.OpeningCost
import com.example.zar.features.crypto.presentation.model.CryptoHeroStatus
import com.example.zar.features.crypto.presentation.model.CryptoOverview
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Figma example (Z89, Z94): 0.005 BTC opening at LKR 140,000, buy 0.01 for 300,000, sell 0.01 for 360,000. */
class CryptoOverviewTest {

    private val start = LocalDate.of(2026, 10, 1)
    private fun lkr(major: String) = FiatMoney.parse(major, Currencies.LKR)!!
    private fun btc(q: String) = CryptoAmount.of(q.toBigDecimal(), Currencies.BTC)
    private val binance = Account(AccountId("binance-btc"), "Binance BTC", AccountType.CRYPTO, Currencies.BTC, OpeningBalance.Approximate(btc("0.005")), start)
    private val openingCost = mapOf(binance.id to OpeningCost(binance.id, lkr("140000"), isApproximate = true, updatedAt = T0))

    private var seconds = 0L
    private fun move(id: String, from: Account, to: Account, sent: Money, received: Money, value: FiatMoney?, day: Int): List<Transaction> {
        val movement = CryptoMovement(CryptoMovementRules.kindOf(from, to)!!, from, to, sent, received, value, start.plusDays(day.toLong()), null)
        val transfer = CryptoMovementRules.newTransfer(TransferId(id), movement, T0.plusSeconds(seconds++))
        return listOf(transfer.outLeg(), transfer.inLeg())
    }

    private val buy = move("buy", bank, binance, lkr("300000"), btc("0.01"), null, 14)
    private val sell = move("sell", binance, bank, btc("0.01"), lkr("360000"), null, 20)

    private fun portfolio(txs: List<Transaction>, accounts: List<Account> = listOf(bank, binance), costs: Map<AccountId, OpeningCost> = openingCost) =
        CryptoPortfolioCalculator.calculate(accounts, LedgerProjector.project(txs, emptyList(), emptyList()), costs)

    @Test
    fun aSaleShowsItsCostRemovedAndTheRealizedCount() {
        val p = portfolio(buy + sell)
        val sale = p.movements.first()
        assertEquals(lkr("360000"), CryptoOverview.lkrValue(sale))
        assertEquals(lkr("293333.33"), CryptoOverview.costRemoved(sale))
        assertEquals(CryptoHeroStatus.Realized(sales = 1, approximate = true), CryptoOverview.heroStatus(p))
    }

    @Test
    fun aBuyHasAValueButNoCostRemoved() {
        val bought = portfolio(buy).movements.single()
        assertEquals(lkr("300000"), CryptoOverview.lkrValue(bought))
        assertNull(CryptoOverview.costRemoved(bought))
        assertEquals(CryptoHeroStatus.NoSalesYet, CryptoOverview.heroStatus(portfolio(buy)))
    }

    @Test
    fun aBuyIsTitledWithTheCoinsReceivedNotTheMoneyPaid() {
        val p = portfolio(buy + sell)
        val bought = p.movements.single { it.kind == CryptoMovementKind.BUY }
        val sold = p.movements.single { it.kind == CryptoMovementKind.SELL }
        assertEquals(btc("0.01"), CryptoOverview.titleAmount(bought))
        assertEquals(btc("0.01"), CryptoOverview.titleAmount(sold))
    }

    @Test
    fun theHeroSaysWhereItsNumberComesFrom() {
        assertEquals(CryptoHeroStatus.NoWallets, CryptoOverview.heroStatus(portfolio(emptyList(), accounts = listOf(bank))))
        assertEquals(CryptoHeroStatus.OpeningBalancesOnly, CryptoOverview.heroStatus(portfolio(emptyList())))
        assertEquals("Opening coins without a cost", CryptoHeroStatus.KnownResultsOnly, CryptoOverview.heroStatus(portfolio(buy, costs = emptyMap())))
    }
}
