package com.example.zar.features.quicknote

import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.features.quicknote.domain.model.QuickNoteAccountContext
import com.example.zar.features.quicknote.domain.model.QuickNoteDraftRequest
import java.time.LocalDate

internal object QuickNoteTestSupport {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 21)

    val boc = QuickNoteAccountContext(AccountId("boc"), "BOC Savings", AccountType.BANK, Currencies.LKR)
    val cash = QuickNoteAccountContext(AccountId("cash"), "Cash wallet", AccountType.CASH, Currencies.LKR)
    val payoneer = QuickNoteAccountContext(AccountId("payoneer"), "Payoneer", AccountType.FREELANCE, Currencies.USD)
    val usdt = QuickNoteAccountContext(AccountId("binance-usdt"), "Binance USDT", AccountType.CRYPTO, Currencies.USDT)

    val accounts = listOf(boc, cash, payoneer, usdt)

    fun request(note: String, accounts: List<QuickNoteAccountContext> = QuickNoteTestSupport.accounts) =
        QuickNoteDraftRequest(note = note, today = TODAY, accounts = accounts)
}
