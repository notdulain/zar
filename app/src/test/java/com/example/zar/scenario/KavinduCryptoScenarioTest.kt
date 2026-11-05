package com.example.zar.scenario

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.Currency
import com.example.zar.core.domain.model.money.Money
import com.example.zar.features.crypto.domain.CryptoMovementRules
import com.example.zar.features.crypto.domain.CryptoPortfolioCalculator
import com.example.zar.features.crypto.domain.model.CryptoMovement
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Kavindu made a gain in Q1 and a loss in Q3 but cannot say his net crypto result
 * (assignment §3.2). These tests check the running net position he is shown.
 */
class KavinduCryptoScenarioTest {

    private val start = LocalDate.of(2026, 1, 1)
    private fun wallet(id: String, currency: Currency) =
        Account(AccountId(id), id, AccountType.CRYPTO, currency, OpeningBalance.Known(CryptoAmount.zero(currency)), start)
    private val eth = wallet("binance-eth", Currencies.ETH)
    private val usdt = wallet("binance-usdt", Currencies.USDT)
    private fun ethOf(q: String) = CryptoAmount.of(q.toBigDecimal(), Currencies.ETH)
    private fun usdtOf(q: String) = CryptoAmount.of(q.toBigDecimal(), Currencies.USDT)

    private var seconds = 0L
    private fun move(id: String, from: Account, to: Account, sent: Money, received: Money, date: LocalDate): List<Transaction> {
        val kind = CryptoMovementRules.kindOf(from, to)!!
        val transfer = CryptoMovementRules.newTransfer(TransferId(id), CryptoMovement(kind, from, to, sent, received, null, date, null), T0.plusSeconds(seconds++))
        return listOf(transfer.outLeg(), transfer.inLeg())
    }

    // Q1: buy 0.1 ETH for LKR 100,000, sell half for LKR 70,000 → +20,000.
    private val q1 = move("buy-eth", bank, eth, lkr(100_000), ethOf("0.1"), LocalDate.of(2026, 2, 3)) +
        move("sell-eth", eth, bank, ethOf("0.05"), lkr(70_000), LocalDate.of(2026, 3, 20))

    // Q3: buy 1,000 USDT for LKR 300,000, sell it all for LKR 285,000 → −15,000.
    private val q3 = move("buy-usdt", bank, usdt, lkr(300_000), usdtOf("1000"), LocalDate.of(2026, 7, 4)) +
        move("sell-usdt", usdt, bank, usdtOf("1000"), lkr(285_000), LocalDate.of(2026, 8, 30))

    private fun portfolio(txs: List<Transaction>) =
        CryptoPortfolioCalculator.calculate(listOf(bank, eth, usdt), LedgerProjector.project(txs, emptyList(), emptyList()), emptyMap())

    @Test
    fun aGainAndALossNetToOneYearToDateFigure() {
        val portfolio = portfolio(q1 + q3)
        assertEquals("+20,000 − 15,000", lkr(5_000), portfolio.realized)
    }

    @Test
    fun whatHeStillHoldsKeepsItsCost() {
        val position = portfolio(q1 + q3).assets.single { it.currency == Currencies.ETH }
        assertEquals(ethOf("0.05"), position.quantity)
        assertEquals("Half of the LKR 100,000 he paid", lkr(50_000), position.knownCost)
    }

    @Test
    fun tradingIsNeverCountedAsIncomeOrSpending() {
        // Buying and selling crypto moves money between his own accounts; profit shows on the Crypto screen only.
        val october = (q1 + q3).map { entry(it) }
        val summary = LedgerSummaryCalculator.summarize(october, start..LocalDate.of(2026, 12, 31))
        assertEquals(lkr(0), summary.received.amount)
        assertEquals(lkr(0), summary.spent.total.amount)
    }
}
