package com.example.zar.features.crypto.domain

import com.example.zar.core.domain.TestLedger.T0
import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.payoneer
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.calculation.LedgerProjector
import com.example.zar.core.domain.calculation.LedgerSummaryCalculator
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.Transaction
import com.example.zar.core.domain.model.TransactionId
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.VoidMarker
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.features.crypto.domain.model.CryptoMovement
import com.example.zar.features.crypto.domain.model.CryptoMovementKind
import com.example.zar.features.crypto.domain.model.OpeningCost
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoPortfolioCalculatorTest {

    private val start = LocalDate.of(2026, 10, 1)
    private fun btc(q: String) = CryptoAmount.of(q.toBigDecimal(), Currencies.BTC)
    private fun usdt(q: String) = CryptoAmount.of(q.toBigDecimal(), Currencies.USDT)
    private fun wallet(id: String, currency: com.example.zar.core.domain.model.money.Currency, opening: OpeningBalance = OpeningBalance.Known(CryptoAmount.zero(currency))) =
        Account(AccountId(id), id, AccountType.CRYPTO, currency, opening, start)

    private val binanceBtc = wallet("binance-btc", Currencies.BTC)
    private val ledgerBtc = wallet("ledger-btc", Currencies.BTC)
    private val binanceUsdt = wallet("binance-usdt", Currencies.USDT)
    private val accounts = listOf(bank, payoneer, binanceBtc, ledgerBtc, binanceUsdt)

    private var seconds = 0L
    private fun move(id: String, from: Account, to: Account, sent: com.example.zar.core.domain.model.money.Money, received: com.example.zar.core.domain.model.money.Money, lkrValue: FiatMoney?, day: Int): List<Transaction> {
        val kind = CryptoMovementRules.kindOf(from, to)!!
        val transfer = CryptoMovementRules.newTransfer(
            TransferId(id),
            CryptoMovement(kind, from, to, sent, received, lkrValue, start.plusDays(day.toLong()), null),
            T0.plusSeconds(seconds++),
        )
        return listOf(transfer.outLeg(), transfer.inLeg())
    }

    private fun calc(txs: List<Transaction>, voids: List<VoidMarker> = emptyList(), costs: Map<AccountId, OpeningCost> = emptyMap(), accountList: List<Account> = accounts) =
        CryptoPortfolioCalculator.calculate(accountList, LedgerProjector.project(txs, emptyList(), voids), costs)

    @Test
    fun averageCostGivesRealizedProfitOnSale() {
        val txs = move("b1", bank, binanceBtc, lkr(300_000), btc("0.01"), null, 1) +
            move("b2", bank, binanceBtc, lkr(340_000), btc("0.01"), null, 2) +
            move("s1", binanceBtc, bank, btc("0.01"), lkr(360_000), null, 3)
        val portfolio = calc(txs)
        val position = portfolio.assets.single { it.currency == Currencies.BTC }

        // Average cost 320,000 per 0.01 BTC; sold for 360,000 → +40,000.
        assertEquals(lkr(40_000), portfolio.realized)
        assertEquals(btc("0.01"), position.quantity)
        assertEquals(lkr(320_000), position.knownCost)
        assertEquals(lkr(32_000_000), position.averageCost)
        assertEquals(0, portfolio.unknownResultCount)
        assertEquals(CryptoMovementKind.SELL, portfolio.movements.first().kind)
        assertEquals(lkr(40_000), portfolio.movements.first().result?.amount)
    }

    @Test
    fun lossIsNegativeAndTradesRealizeOnTheAssetGivenUp() {
        val txs = move("b1", bank, binanceUsdt, lkr(300_000), usdt("1000"), null, 1) +
            // Trade 500 USDT (cost 150,000) for BTC worth 140,000 → -10,000 on USDT.
            move("t1", binanceUsdt, binanceBtc, usdt("500"), btc("0.005"), lkr(140_000), 2)
        val portfolio = calc(txs)
        assertEquals(lkr(-10_000), portfolio.realized)
        assertEquals(lkr(140_000), portfolio.assets.single { it.currency == Currencies.BTC }.knownCost)
        assertEquals(usdt("500"), portfolio.assets.single { it.currency == Currencies.USDT }.quantity)
    }

    @Test
    fun movingBetweenOwnWalletsRealizesNothingAndKeepsCost() {
        val txs = move("b1", bank, binanceBtc, lkr(300_000), btc("0.01"), null, 1) +
            move("m1", binanceBtc, ledgerBtc, btc("0.01"), btc("0.01"), null, 2)
        val portfolio = calc(txs)
        val position = portfolio.assets.single { it.currency == Currencies.BTC }
        assertEquals(lkr(0), portfolio.realized)
        assertEquals(0, portfolio.unknownResultCount)
        assertEquals(btc("0.01"), position.quantity)
        assertEquals(lkr(300_000), position.knownCost)
        assertEquals(CryptoMovementKind.MOVE, portfolio.movements.first().kind)
        assertNull(portfolio.movements.first().result)
    }

    @Test
    fun moveThatArrivesShortLosesTheDifferenceWithoutARealizedResult() {
        // 0.01 BTC leaves Binance but 0.009 BTC reaches Ledger: 0.001 BTC went in a network fee.
        val txs = move("b1", bank, binanceBtc, lkr(300_000), btc("0.01"), null, 1) +
            move("m1", binanceBtc, ledgerBtc, btc("0.01"), btc("0.009"), null, 2)
        val portfolio = calc(txs)
        val position = portfolio.assets.single { it.currency == Currencies.BTC }
        assertEquals(btc("0.009"), position.quantity)
        assertEquals(lkr(270_000), position.knownCost)
        assertEquals(lkr(30_000_000), position.averageCost)
        assertEquals(lkr(0), portfolio.realized)
        assertEquals(0, portfolio.unknownResultCount)
    }

    @Test
    fun moveThatArrivesWithMoreAddsTheExtraCoinsAtAnUnknownCost() {
        val txs = move("b1", bank, binanceBtc, lkr(300_000), btc("0.01"), null, 1) +
            move("m1", binanceBtc, ledgerBtc, btc("0.01"), btc("0.011"), null, 2)
        val position = calc(txs).assets.single { it.currency == Currencies.BTC }
        assertEquals(btc("0.011"), position.quantity)
        assertEquals(btc("0.001"), position.unknownCostQuantity)
        assertEquals(lkr(300_000), position.knownCost)
        assertNull(position.averageCost)
    }

    @Test
    fun cryptoMovesNeverCountAsIncomeOrSpending() {
        val txs = move("b1", bank, binanceBtc, lkr(300_000), btc("0.01"), null, 1) +
            move("s1", binanceBtc, bank, btc("0.01"), lkr(360_000), null, 3)
        val month = YearMonth.of(2026, 10)
        val summary = LedgerSummaryCalculator.summarize(LedgerProjector.project(txs, emptyList(), emptyList()), month.atDay(1)..month.atEndOfMonth())
        assertEquals(lkr(0), summary.received.amount)
        assertEquals(lkr(0), summary.spent.total.amount)
    }

    @Test
    fun missingValuesMakeResultsUnknownInsteadOfGuessed() {
        val txs = move("b1", payoneer, binanceBtc, usd(1_000), btc("0.01"), null, 1) +
            move("s1", binanceBtc, bank, btc("0.01"), lkr(360_000), null, 2)
        val portfolio = calc(txs)
        assertEquals(lkr(0), portfolio.realized)
        assertEquals(1, portfolio.unknownResultCount)
        assertFalse(portfolio.movements.first().result!!.isKnown)
    }

    @Test
    fun unknownOpeningQuantityAndCostAreReported() {
        val unknownQty = wallet("old-btc", Currencies.BTC, OpeningBalance.Unknown)
        val approxQty = wallet("approx-btc", Currencies.BTC, OpeningBalance.Approximate(btc("0.02")))
        val portfolio = calc(emptyList(), accountList = listOf(unknownQty, approxQty))
        val position = portfolio.assets.single()
        assertFalse(position.isQuantityComplete)
        assertTrue(position.isCostApproximate)
        assertEquals(btc("0.02"), position.unknownCostQuantity)
        assertNull("Cost of the opening coins is unknown", position.averageCost)
    }

    @Test
    fun openingCostLetsOpeningCoinsBeSoldWithAKnownResult() {
        val held = wallet("binance-btc", Currencies.BTC, OpeningBalance.Known(btc("0.02")))
        val cost = OpeningCost(held.id, lkr(500_000), isApproximate = false, updatedAt = T0)
        val sale = move("s1", held, bank, btc("0.01"), lkr(300_000), null, 2)

        val withoutCost = calc(sale, accountList = listOf(bank, held))
        assertEquals(1, withoutCost.unknownResultCount)

        val withCost = calc(sale, costs = mapOf(held.id to cost), accountList = listOf(bank, held))
        assertEquals(lkr(50_000), withCost.realized)
        assertEquals(lkr(250_000), withCost.assets.single().knownCost)
    }

    @Test
    fun sellingMoreThanRecordedIsUnknown() {
        val txs = move("b1", bank, binanceBtc, lkr(300_000), btc("0.01"), null, 1) +
            move("s1", binanceBtc, bank, btc("0.02"), lkr(700_000), null, 2)
        val portfolio = calc(txs)
        assertEquals(1, portfolio.unknownResultCount)
        assertEquals(btc("0"), portfolio.assets.single { it.currency == Currencies.BTC }.quantity)
    }

    @Test
    fun undoingASaleRemovesItsResult() {
        val txs = move("b1", bank, binanceBtc, lkr(300_000), btc("0.01"), null, 1) +
            move("s1", binanceBtc, bank, btc("0.01"), lkr(360_000), null, 3)
        val voids = listOf(VoidMarker(TransactionId("s1-out"), T0), VoidMarker(TransactionId("s1-in"), T0))
        val portfolio = calc(txs, voids)
        assertEquals(lkr(0), portfolio.realized)
        assertEquals(btc("0.01"), portfolio.assets.single { it.currency == Currencies.BTC }.quantity)
        assertEquals(1, portfolio.movements.size)
    }
}
