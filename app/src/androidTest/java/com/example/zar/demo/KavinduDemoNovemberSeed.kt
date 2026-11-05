package com.example.zar.demo

import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.BuildConfig
import com.example.zar.core.data.remote.FirebaseUserSessionProvider
import com.example.zar.core.data.remote.firestore.FirestoreLedgerRepository
import com.example.zar.core.domain.model.*
import com.example.zar.core.domain.model.money.*
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Not a test: adds the first days of November 2026 to the account that [KavinduDemoSeed] already filled, so the current
 * month's Home and Activity are not empty. It reuses that seed's accounts, writes only dated entries up to 5 November,
 * and refuses to run twice. It is invoked the same way, naming this class:
 *
 *   adb shell am instrument -w -e class com.example.zar.demo.KavinduDemoNovemberSeed -e email … -e password … [-e live true] \
 *     com.example.zar.test/androidx.test.runner.AndroidJUnitRunner
 */
class KavinduDemoNovemberSeed {
    private val t0 = Instant.parse("2026-11-01T03:00:00Z")
    private var tick = 0L
    private fun at() = t0.plusSeconds(60 * tick++)
    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private fun lkr(v: Long) = FiatMoney(v * 100, Currencies.LKR)
    private fun usd(v: Long) = FiatMoney(v * 100, Currencies.USD)

    @Test fun seedNovember() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val email = args.getString("email")
        val password = args.getString("password")
        val live = args.getString("live") == "true"
        assumeTrue("Select the matching Firebase build", BuildConfig.USE_FIREBASE_EMULATORS != live)
        assumeTrue("pass -e email and -e password", email != null && password != null)
        if (live) require(email == "kavindu.silva.demo@example.com") { "Live seeding is restricted to the demo account" }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.fromResource(context)!!, "kavindu-nov-${System.nanoTime()}")
        val auth = FirebaseAuth.getInstance(app).also { if (!live) it.useEmulator(BuildConfig.EMULATOR_HOST, 9099) }
        val db = FirebaseFirestore.getInstance(app).also { if (!live) it.useEmulator(BuildConfig.EMULATOR_HOST, 8080) }
        auth.signInWithEmailAndPassword(email!!, password!!).await()
        val transactions = db.collection("users").document(auth.currentUser!!.uid).collection("transactions")
        require(transactions.document("in-salary-oct").get(Source.SERVER).await().exists()) { "Run KavinduDemoSeed first" }
        require(!transactions.document("in-nov-freelance").get(Source.SERVER).await().exists()) { "November was already seeded" }

        val save = SaveTransactionUseCase(FirestoreLedgerRepository(db, FirebaseUserSessionProvider(auth)))
        // The same accounts KavinduDemoSeed created; only their ids, currencies and opening data matter here.
        fun account(id: String, name: String, type: AccountType, currency: Currency, opening: Money) =
            Account(AccountId(id), name, type, currency, OpeningBalance.Known(opening), LocalDate.of(2026, 9, 30))
        val combank = account("combank", "Commercial Bank", AccountType.BANK, Currencies.LKR, lkr(42_000))
        val cash = account("cash", "Cash", AccountType.CASH, Currencies.LKR, lkr(3_000))
        val payoneer = account("payoneer", "Payoneer USD", AccountType.FREELANCE, Currencies.USD, usd(0))

        suspend fun income(id: String, acc: Account, amount: Money, day: Int, from: String, category: String, rate: String? = null) {
            val conversion = if (amount.currency == Currencies.LKR) ReportingConversion.SameCurrency else
                com.example.zar.core.domain.calculation.CurrencyConverter.toReporting(amount,
                    ExchangeRate(amount.currency, Currencies.LKR, rate!!.toBigDecimal(), at(), RateSource.MANUAL))
            save(Transaction(TransactionId(id), acc.id, TransactionType.INCOME, amount, nov(day), conversion,
                categoryId = category, merchant = from, createdAt = at()), acc).getOrThrow()
        }
        suspend fun spend(id: String, acc: Account, amount: Long, day: Int, merchant: String, category: String, cls: SpendingClass) =
            save(Transaction(TransactionId(id), acc.id, TransactionType.EXPENSE, lkr(amount), nov(day), ReportingConversion.SameCurrency,
                cls, categoryId = category, merchant = merchant, createdAt = at()), acc).getOrThrow()

        // Received: a small freelance job and a converted AdSense payout. The 25 November salary has not happened yet.
        income("in-nov-freelance", combank, lkr(28_000), 2, "Colombo Cafe logo design", "freelance")
        income("in-nov-adsense", payoneer, usd(30), 4, "Google AdSense", "other", rate = "299.00")

        // Spent: rent, a subscription, groceries, coffee, a ride and one unsorted cash purchase.
        spend("ex-nov-rent", combank, 34_000, 1, "Rent - Nugegoda annex", "rent", SpendingClass.COMMITTED)
        spend("ex-nov-spotify", combank, 1_299, 2, "Spotify", "subscriptions", SpendingClass.COMMITTED)
        spend("ex-nov-keells", combank, 6_480, 3, "Keells Super", "groceries", SpendingClass.DISCRETIONARY)
        spend("ex-nov-coffee", combank, 2_150, 4, "Commons Coffee", "coffee-dining", SpendingClass.DISCRETIONARY)
        spend("ex-nov-pickme", combank, 940, 5, "PickMe", "transport", SpendingClass.DISCRETIONARY)
        spend("ex-nov-kade", cash, 750, 5, "Kade (cash)", "other", SpendingClass.UNKNOWN)

        db.waitForPendingWrites().await()
        for (id in listOf("in-nov-freelance", "in-nov-adsense", "ex-nov-rent", "ex-nov-pickme", "ex-nov-kade")) {
            require(transactions.document(id).get(Source.SERVER).await().exists()) { "Firestore did not retain transactions/$id" }
        }
        db.terminate().await()
        app.delete()
    }
}
