package com.example.zar.demo

import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.BuildConfig
import com.example.zar.core.data.remote.FirebaseUserSessionProvider
import com.example.zar.core.data.remote.firestore.FirestoreLedgerRepository
import com.example.zar.core.domain.model.Account
import com.example.zar.core.domain.model.AccountId
import com.example.zar.core.domain.model.AccountType
import com.example.zar.core.domain.model.OpeningBalance
import com.example.zar.core.domain.model.TransferId
import com.example.zar.core.domain.model.money.CryptoAmount
import com.example.zar.core.domain.model.money.Currencies
import com.example.zar.core.domain.model.money.FiatMoney
import com.example.zar.core.domain.model.money.Money
import com.example.zar.core.domain.usecase.SaveTransferUseCase
import com.example.zar.features.accounts.data.repository.FirestoreAccountRepository
import com.example.zar.features.crypto.data.repository.FirestoreOpeningCostRepository
import com.example.zar.features.crypto.domain.CryptoMovementRules
import com.example.zar.features.crypto.domain.model.CryptoMovement
import com.example.zar.features.crypto.domain.model.OpeningCost
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Not a test: loads the Figma Crypto example (Z89, Z94) into an account on the local Firebase emulators, through the
 * production repositories and use cases, so the Crypto screens can be seen and shown with real-looking data:
 *
 * - Binance BTC opened with about 0.005 BTC that cost about LKR 140,000; Ledger BTC and Binance USDT start empty.
 * - 15 Oct: bought 0.01 BTC for LKR 300,000. 18 Oct: bought 500 USDT for LKR 150,000.
 * - 21 Oct: sold 0.01 BTC for LKR 360,000 — a realized profit of LKR 66,666.67 at average cost.
 *
 * It only runs on an emulator build and when asked for by name with `-e email` and `-e password`:
 *
 *   adb shell am instrument -w -e class com.example.zar.demo.CryptoDemoSeed -e email … -e password … \
 *     com.example.zar.test/androidx.test.runner.AndroidJUnitRunner
 */
class CryptoDemoSeed {
    private val t0 = Instant.parse("2026-10-01T03:00:00Z")
    private var tick = 0L
    private fun at() = t0.plusSeconds(60 * tick++)
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private fun lkr(v: Long) = FiatMoney(v * 100, Currencies.LKR)
    private fun btc(q: String) = CryptoAmount.of(q.toBigDecimal(), Currencies.BTC)
    private fun usdt(q: String) = CryptoAmount.of(q.toBigDecimal(), Currencies.USDT)

    @Test fun seed() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val email = args.getString("email")
        val password = args.getString("password")
        assumeTrue("emulator build only", BuildConfig.USE_FIREBASE_EMULATORS)
        assumeTrue("pass -e email and -e password", email != null && password != null)

        // A client of our own, pointed at the emulators before first use, with the app's real project id so the
        // app sees the data. It can never reach the live project.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.fromResource(context)!!, "crypto-seed-${System.nanoTime()}")
        val auth = FirebaseAuth.getInstance(app).also { it.useEmulator(BuildConfig.EMULATOR_HOST, 9099) }
        val db = FirebaseFirestore.getInstance(app).also { it.useEmulator(BuildConfig.EMULATOR_HOST, 8080) }
        runCatching { auth.signInWithEmailAndPassword(email!!, password!!).await() }
            .getOrElse { auth.createUserWithEmailAndPassword(email!!, password!!).await() }

        val session = FirebaseUserSessionProvider(auth)
        val accounts = FirestoreAccountRepository(db, session)
        val transfer = SaveTransferUseCase(FirestoreLedgerRepository(db, session))
        val since = LocalDate.of(2026, 9, 30)

        val bank = Account(AccountId("demo-bank"), "Bank", AccountType.BANK, Currencies.LKR, OpeningBalance.Known(lkr(900_000)), since)
        val binanceBtc = Account(AccountId("demo-binance-btc"), "Binance BTC", AccountType.CRYPTO, Currencies.BTC, OpeningBalance.Approximate(btc("0.005")), since)
        val ledgerBtc = Account(AccountId("demo-ledger-btc"), "Ledger BTC", AccountType.CRYPTO, Currencies.BTC, OpeningBalance.Known(CryptoAmount.zero(Currencies.BTC)), since)
        val binanceUsdt = Account(AccountId("demo-binance-usdt"), "Binance USDT", AccountType.CRYPTO, Currencies.USDT, OpeningBalance.Known(CryptoAmount.zero(Currencies.USDT)), since)
        listOf(bank, binanceBtc, ledgerBtc, binanceUsdt).forEach { accounts.saveAccount(it) }

        FirestoreOpeningCostRepository(db, session).saveOpeningCost(OpeningCost(binanceBtc.id, lkr(140_000), isApproximate = true, updatedAt = at()))

        suspend fun move(id: String, from: Account, to: Account, sent: Money, received: Money, day: Int) {
            val movement = CryptoMovement(CryptoMovementRules.kindOf(from, to)!!, from, to, sent, received, null, oct(day), null)
            transfer(CryptoMovementRules.newTransfer(TransferId(id), movement, at()), from, to).getOrThrow()
        }
        move("demo-buy-btc", bank, binanceBtc, lkr(300_000), btc("0.01"), 15)
        move("demo-buy-usdt", bank, binanceUsdt, lkr(150_000), usdt("500"), 18)
        move("demo-sell-btc", binanceBtc, bank, btc("0.01"), lkr(360_000), 21)

        db.waitForPendingWrites().await()
        db.terminate().await()
        app.delete()
    }
}
