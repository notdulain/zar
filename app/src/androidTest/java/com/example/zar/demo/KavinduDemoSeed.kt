package com.example.zar.demo

import androidx.test.platform.app.InstrumentationRegistry
import com.example.zar.BuildConfig
import com.example.zar.core.data.remote.FirebaseUserSessionProvider
import com.example.zar.core.data.remote.firestore.FirestoreExpectedPaymentRepository
import com.example.zar.core.data.remote.firestore.FirestoreLedgerRepository
import com.example.zar.core.domain.model.*
import com.example.zar.core.domain.model.money.*
import com.example.zar.core.domain.usecase.SaveTransactionUseCase
import com.example.zar.core.domain.usecase.SaveTransferUseCase
import com.example.zar.features.accounts.data.repository.FirestoreAccountRepository
import com.example.zar.features.allowance.data.repository.FirestoreAllowanceRepository
import com.example.zar.features.allowance.domain.model.AllowanceSettings
import com.example.zar.features.bills.data.repository.FirestoreBillRepository
import com.example.zar.features.bills.domain.model.*
import com.example.zar.features.crypto.domain.CryptoMovementRules
import com.example.zar.features.crypto.domain.model.CryptoMovement
import com.example.zar.features.freelance.data.repository.FirestoreProjectRepository
import com.example.zar.features.freelance.domain.model.Project
import com.example.zar.features.freelance.domain.model.ProjectId
import com.example.zar.features.freelance.domain.model.ProjectStatus
import com.example.zar.features.plans.data.repository.FirestoreGoalRepository
import com.example.zar.features.plans.domain.model.GoalAllocation
import com.example.zar.features.plans.domain.model.GoalId
import com.example.zar.features.plans.domain.model.MACBOOK_GOAL_ID
import com.example.zar.features.plans.domain.model.SavingsGoal
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
 * Not a test: loads Kavindu Silva's October 2026 (the assignment scenario) into the app's account on the local
 * Firebase emulators, through the production repositories and use cases, for the demo and report screenshots.
 * It only runs on an emulator build and when asked for by name with `-e email` and `-e password`:
 *
 *   adb shell am instrument -w -e class com.example.zar.demo.KavinduDemoSeed -e email … -e password … \
 *     com.example.zar.test/androidx.test.runner.AndroidJUnitRunner
 */
class KavinduDemoSeed {
    private val t0 = Instant.parse("2026-10-01T03:00:00Z")
    private var tick = 0L
    private fun at() = t0.plusSeconds(60 * tick++)
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private fun lkr(v: Long) = FiatMoney(v * 100, Currencies.LKR)
    private fun usd(v: Long) = FiatMoney(v * 100, Currencies.USD)

    @Test fun seed() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        val email = args.getString("email")
        val password = args.getString("password")
        assumeTrue("emulator build only", BuildConfig.USE_FIREBASE_EMULATORS)
        assumeTrue("pass -e email and -e password", email != null && password != null)

        // A client of our own, pointed at the emulators before first use, with the app's real project id so the
        // app sees the data. It can never reach the live project.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.fromResource(context)!!, "kavindu-seed-${System.nanoTime()}")
        val auth = FirebaseAuth.getInstance(app).also { it.useEmulator(BuildConfig.EMULATOR_HOST, 9099) }
        val db = FirebaseFirestore.getInstance(app).also { it.useEmulator(BuildConfig.EMULATOR_HOST, 8080) }
        runCatching { auth.signInWithEmailAndPassword(email!!, password!!).await() }
            .getOrElse { auth.createUserWithEmailAndPassword(email!!, password!!).await() }

        val session = FirebaseUserSessionProvider(auth)
        val accounts = FirestoreAccountRepository(db, session)
        val ledger = FirestoreLedgerRepository(db, session)
        val save = SaveTransactionUseCase(ledger)
        val transfer = SaveTransferUseCase(ledger)
        val expected = FirestoreExpectedPaymentRepository(db, session)

        // --- Accounts -------------------------------------------------------------------------------
        fun account(id: String, name: String, type: AccountType, currency: Currency, opening: Money) =
            Account(AccountId(id), name, type, currency, OpeningBalance.Known(opening), LocalDate.of(2026, 9, 30))
        val combank = account("combank", "Commercial Bank", AccountType.BANK, Currencies.LKR, lkr(42_000))
        val sampath = account("sampath", "Sampath Savings", AccountType.BANK, Currencies.LKR, lkr(11_200))
        val cash = account("cash", "Cash", AccountType.CASH, Currencies.LKR, lkr(3_000))
        val payoneer = account("payoneer", "Payoneer USD", AccountType.FREELANCE, Currencies.USD, usd(0))
        val binance = Account(AccountId("binance-usdt"), "Binance USDT", AccountType.CRYPTO, Currencies.USDT,
            OpeningBalance.Known(CryptoAmount.zero(Currencies.USDT)), LocalDate.of(2026, 9, 30))
        listOf(combank, sampath, cash, payoneer, binance).forEach { accounts.saveAccount(it) }

        // --- Received in October: four channels, two currencies ------------------------------------
        suspend fun income(id: String, acc: Account, amount: Money, day: Int, from: String, category: String, rate: String? = null) {
            val conversion = when {
                amount.currency == Currencies.LKR -> ReportingConversion.SameCurrency
                rate == null -> ReportingConversion.Missing(Currencies.LKR)
                else -> com.example.zar.core.domain.calculation.CurrencyConverter.toReporting(amount,
                    ExchangeRate(amount.currency, Currencies.LKR, rate.toBigDecimal(), at(), RateSource.MANUAL))
            }
            val tx = Transaction(TransactionId(id), acc.id, TransactionType.INCOME, amount, oct(day), conversion,
                categoryId = category, merchant = from, createdAt = at())
            save(tx, acc).getOrThrow()
        }
        income("in-salary-oct", combank, lkr(132_500), 25, "Northwind Labs (salary)", "salary")
        income("in-bakers-design", combank, lkr(45_000), 9, "Lanka Bakers", "freelance")
        income("in-adsense-oct", payoneer, usd(42), 21, "Google AdSense", "other", rate = "298.50")
        income("in-upwork-tip", payoneer, usd(15), 28, "Upwork client tip", "freelance")

        // --- Spent in October: committed, discretionary and unsorted -------------------------------
        suspend fun spend(id: String, acc: Account, amount: Long, day: Int, merchant: String, category: String, cls: SpendingClass) =
            save(Transaction(TransactionId(id), acc.id, TransactionType.EXPENSE, lkr(amount), oct(day), ReportingConversion.SameCurrency,
                cls, categoryId = category, merchant = merchant, createdAt = at()), acc).getOrThrow()
        spend("ex-rent", combank, 34_000, 1, "Rent - Nugegoda annex", "rent", SpendingClass.COMMITTED)
        spend("ex-gym", sampath, 6_500, 3, "Power World Gym", "bills", SpendingClass.COMMITTED)
        spend("ex-dialog", combank, 5_850, 25, "Dialog Fibre", "bills", SpendingClass.COMMITTED)
        spend("ex-ceb", combank, 4_200, 12, "CEB Electricity", "bills", SpendingClass.COMMITTED)
        spend("ex-netflix-oct", combank, 2_643, 9, "NETFLIX.COM", "subscriptions", SpendingClass.COMMITTED)
        // September's charge too, so Netflix is suggested as a subscription.
        save(Transaction(TransactionId("ex-netflix-sep9"), combank.id, TransactionType.EXPENSE, lkr(2_643), LocalDate.of(2026, 9, 9),
            ReportingConversion.SameCurrency, SpendingClass.COMMITTED, "subscriptions", "NETFLIX.COM", createdAt = at()), combank).getOrThrow()
        spend("ex-spotify", combank, 1_299, 2, "Spotify", "subscriptions", SpendingClass.COMMITTED)
        listOf(
            Triple("ex-coffee-1", 3, 1_850L), Triple("ex-coffee-2", 7, 2_400L), Triple("ex-coffee-3", 11, 3_150L),
            Triple("ex-coffee-4", 15, 4_600L), Triple("ex-coffee-5", 18, 2_200L), Triple("ex-coffee-6", 22, 5_300L),
            Triple("ex-coffee-7", 26, 4_500L),
        ).forEachIndexed { i, (id, day, amount) ->
            spend(id, combank, amount, day, listOf("Barista Kollupitiya", "Café Kumbuk", "Commons Coffee", "Ministry of Crab")[i % 4],
                "coffee-dining", SpendingClass.DISCRETIONARY)
        }
        spend("ex-ubereats-1", combank, 2_350, 10, "Uber Eats", "coffee-dining", SpendingClass.DISCRETIONARY)
        spend("ex-ubereats-2", combank, 1_980, 24, "Uber Eats", "coffee-dining", SpendingClass.DISCRETIONARY)
        spend("ex-pickme-1", combank, 850, 6, "PickMe", "transport", SpendingClass.DISCRETIONARY)
        spend("ex-pickme-2", combank, 1_240, 16, "PickMe", "transport", SpendingClass.DISCRETIONARY)
        spend("ex-keells", combank, 7_820, 19, "Keells Super", "groceries", SpendingClass.DISCRETIONARY)
        spend("ex-kade", cash, 1_600, 14, "Kade (cash)", "other", SpendingClass.UNKNOWN)

        // --- Crypto: LKR into USDT on Binance (a transfer, never income or spending) ----------------
        val buy = CryptoMovementRules.newTransfer(TransferId("tr-usdt-buy"),
            CryptoMovement(CryptoMovementRules.kindOf(combank, binance)!!, combank, binance, lkr(60_000),
                CryptoAmount.of("200".toBigDecimal(), Currencies.USDT), null, oct(5), null), at())
        transfer(buy, combank, binance).getOrThrow()

        // --- Bills: rent, the gym on the secondary account, internet -------------------------------
        val bills = FirestoreBillRepository(db, session)
        suspend fun bill(id: String, name: String, acc: Account, amount: Long, first: LocalDate, reminder: Int? = null) =
            bills.saveBill(Bill(BillId(id), name, acc.id, lkr(amount), BillFrequency.MONTHLY, first, BillStatus.ACTIVE, at(),
                reminder = reminder?.let { r -> BillReminder.entries.first { it.daysBefore == r } }))
        bill("bill-rent", "Rent", combank, 34_000, LocalDate.of(2026, 9, 1), reminder = 3)
        bill("bill-gym", "Power World Gym", sampath, 6_500, LocalDate.of(2026, 9, 3))
        bill("bill-dialog", "Dialog Fibre", combank, 5_850, LocalDate.of(2026, 9, 25), reminder = 1)

        // --- Freelance: milestones he used to forget to chase --------------------------------------
        val projects = FirestoreProjectRepository(db, session)
        projects.saveProject(Project(ProjectId("p-bakers"), "Lanka Bakers website", "Lanka Bakers", Currencies.LKR, ProjectStatus.ACTIVE, at()))
        projects.saveProject(Project(ProjectId("p-ceylontea"), "React stock dashboard", "Ceylon Tea Traders", Currencies.LKR, ProjectStatus.ACTIVE, at()))
        fun milestone(id: String, project: String, title: String, amount: Long, due: LocalDate) =
            ExpectedPayment(ExpectedPaymentId(id), Direction.IN, lkr(amount), due, title,
                ExpectedSource.FreelanceMilestone(project, id), status = ExpectedPaymentStatus.OPEN, createdAt = at())
        expected.saveExpectedPayment(milestone("m-bakers-design", "p-bakers", "Design approved", 45_000, oct(8)))
        expected.saveExpectedPayment(milestone("m-bakers-launch", "p-bakers", "Site launched", 45_000, oct(30)))
        expected.saveExpectedPayment(milestone("m-tea-mvp", "p-ceylontea", "MVP demo", 60_000, LocalDate.of(2026, 11, 20)))
        expected.saveMatch(PaymentMatch(ExpectedPaymentId("m-bakers-design"), TransactionId("in-bakers-design"), lkr(45_000), at()))

        // --- The MacBook goal and a weekly limit for discretionary spending ------------------------
        FirestoreGoalRepository(db, session).saveGoal(SavingsGoal(GoalId(MACBOOK_GOAL_ID), "MacBook Pro M4", lkr(490_000),
            listOf(GoalAllocation(sampath.id, lkr(11_200))), LocalDate.of(2027, 9, 30)))
        FirestoreAllowanceRepository(db, session).saveSettings(AllowanceSettings(true, lkr(8_000)))

        db.waitForPendingWrites().await()
        db.terminate().await()
        app.delete()
    }
}
