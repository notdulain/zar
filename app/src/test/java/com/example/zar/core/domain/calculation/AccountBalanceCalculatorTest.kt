package com.example.zar.core.domain.calculation

import com.example.zar.core.domain.TestLedger.bank
import com.example.zar.core.domain.TestLedger.entry
import com.example.zar.core.domain.TestLedger.lkr
import com.example.zar.core.domain.TestLedger.tx
import com.example.zar.core.domain.TestLedger.usd
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountBalanceCalculatorTest {

    private val entries = listOf(
        entry(tx("in", TransactionType.INCOME, lkr(5_000))),
        entry(tx("out", TransactionType.EXPENSE, lkr(1_000))),
        entry(tx("undone", TransactionType.EXPENSE, lkr(700)), voided = true),
        entry(tx("before-opening", TransactionType.EXPENSE, lkr(300), date = LocalDate.of(2026, 9, 1))),
    )

    @Test
    fun openingPlusActiveMovements() {
        val balance = AccountBalanceCalculator.calculate(bank, entries)
        assertEquals(lkr(14_000), balance.current)
        assertEquals(lkr(4_000), balance.netMovement)
    }

    @Test
    fun unknownOpeningGivesNoCurrentBalance() {
        val balance = AccountBalanceCalculator.calculate(bank.copy(openingBalance = OpeningBalance.Unknown), entries)
        assertNull(balance.current)
        assertTrue(balance.isApproximate)
        assertEquals(lkr(4_000), balance.netMovement)
    }

    @Test
    fun otherCurrencyEntriesAreFlagged() {
        val balance = AccountBalanceCalculator.calculate(bank, listOf(entry(tx("usd", TransactionType.INCOME, usd(1)))))
        assertEquals(1, balance.mismatchedCount)
        assertEquals(lkr(10_000), balance.current)
    }
}
