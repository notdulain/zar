package com.example.zar.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SCRUM-21 / D08: screens and ViewModels must not reach storage or concrete data code, domain
 * stays free of framework and outer layers, and domain tests run as plain JVM tests.
 *
 * The first group scans the real source tree. The second group feeds the scanner small
 * synthetic sources to prove a regression is reported with its location and that legitimate
 * code (domain repository interfaces, Hilt Compose helpers, system-settings observers) passes.
 */
class LayerBoundaryTest {

    /** Known upward import, kept visible instead of silently ignored. Remove when the helper moves to `core/domain`. */
    private val dataToPresentationAllowlist = setOf(
        "features/reminders/data/PaymentReminders.kt",
    )

    // ---- real source tree -------------------------------------------------------------------

    @Test
    fun presentationNeverImportsStorageSdksOrConcreteDataCode() {
        val root = LayerBoundaryScanner.productionRoot()
        val files = LayerBoundaryScanner.kotlinFiles(root).filter(LayerBoundaryScanner::isPresentation)
        assertTrue("Scan must not be vacuous, found ${files.size} presentation files", files.size > 100)

        val violations = files.flatMap { LayerBoundaryScanner.scan(it, root, LayerBoundaryScanner.presentationRules) }

        assertTrue(
            "Screens and ViewModels must go through domain use cases and repository interfaces:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun everyViewModelLivesInPresentation() {
        val root = LayerBoundaryScanner.productionRoot()
        val misplaced = LayerBoundaryScanner.kotlinFiles(root)
            .filterNot(LayerBoundaryScanner::isPresentation)
            .filter { file -> file.readLines().any { "@HiltViewModel" in it || Regex("\\)\\s*:\\s*ViewModel\\(").containsMatchIn(it) } }
            .map { it.relativeTo(root).path }

        assertTrue("ViewModels belong in presentation packages: $misplaced", misplaced.isEmpty())
    }

    @Test
    fun domainHasNoFrameworkStorageOrOuterLayerDependency() {
        val root = LayerBoundaryScanner.productionRoot()
        val files = LayerBoundaryScanner.kotlinFiles(root).filter(LayerBoundaryScanner::isDomain)
        assertTrue("Scan must not be vacuous, found ${files.size} domain files", files.size > 100)

        val violations = files.flatMap { LayerBoundaryScanner.scan(it, root, LayerBoundaryScanner.domainRules) }

        assertTrue(
            "Domain code must stay pure Kotlin and independent of data and presentation:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun dataLayerDoesNotDependOnPresentationExceptTheKnownFormatter() {
        val root = LayerBoundaryScanner.productionRoot()
        val files = LayerBoundaryScanner.kotlinFiles(root).filter(LayerBoundaryScanner::isData)
        assertTrue("Scan must not be vacuous, found ${files.size} data files", files.size > 30)

        val violations = files.flatMap { LayerBoundaryScanner.scan(it, root, LayerBoundaryScanner.dataRules) }
        val unexpected = violations.filter { it.path.replace('\\', '/') !in dataToPresentationAllowlist }
        val stale = dataToPresentationAllowlist - violations.map { it.path.replace('\\', '/') }.toSet()

        assertTrue("Data code must not import presentation code:\n${unexpected.joinToString("\n")}", unexpected.isEmpty())
        assertTrue("Allowlist entries no longer needed, remove them: $stale", stale.isEmpty())
    }

    @Test
    fun domainTestsRunWithoutFirebaseOrAndroidRuntime() {
        val root = LayerBoundaryScanner.testRoot()
        val files = LayerBoundaryScanner.kotlinFiles(root).filter(LayerBoundaryScanner::isDomain)
        assertTrue("Scan must not be vacuous, found ${files.size} domain test files", files.size > 40)

        val violations = files.flatMap { LayerBoundaryScanner.scan(it, root, LayerBoundaryScanner.domainTestRules) }

        assertTrue(
            "Domain tests must run as plain JVM tests without Android or Firebase:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    // ---- scanner behaviour ------------------------------------------------------------------

    @Test
    fun reportsFirebaseImportInViewModelWithLocation() {
        val source = """
            package com.example.zar.features.demo.presentation

            import androidx.lifecycle.ViewModel
            import com.google.firebase.firestore.FirebaseFirestore

            class DemoViewModel(private val db: FirebaseFirestore) : ViewModel()
        """.trimIndent().lines()

        val found = LayerBoundaryScanner.scan("features/demo/presentation/DemoViewModel.kt", source, LayerBoundaryScanner.presentationRules)

        assertEquals(1, found.size)
        assertEquals("features/demo/presentation/DemoViewModel.kt:4 [firebase-sdk] import com.google.firebase.firestore.FirebaseFirestore", found.single().toString())
    }

    @Test
    fun reportsRoomAndSqliteAccessFromScreens() {
        val source = listOf(
            "import androidx.room.Room",
            "import android.database.sqlite.SQLiteDatabase",
            "import android.database.Cursor",
            "import net.zetetic.database.sqlcipher.SupportOpenHelperFactory",
        )

        val rules = LayerBoundaryScanner.scan("DemoScreen.kt", source, LayerBoundaryScanner.presentationRules).map { it.rule }

        assertEquals(listOf("room", "android-sqlite", "android-cursor", "sqlcipher"), rules)
    }

    @Test
    fun reportsConcreteDataImplementationAndHiltModuleInPresentation() {
        val source = listOf(
            "import com.example.zar.core.data.remote.firestore.FirestoreLedgerRepository",
            "import com.example.zar.features.capture.data.auto.SmsStaging",
            "import com.example.zar.di.RepositoryModule",
        )

        val rules = LayerBoundaryScanner.scan("DemoScreen.kt", source, LayerBoundaryScanner.presentationRules).map { it.rule }

        assertEquals(listOf("concrete-data-layer", "concrete-data-layer", "hilt-wiring-module"), rules)
    }

    @Test
    fun reportsFullyQualifiedStorageAccessInsideCode() {
        val source = listOf(
            "class DemoViewModel {",
            "    private val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()",
            "}",
        )

        val found = LayerBoundaryScanner.scan("DemoViewModel.kt", source, LayerBoundaryScanner.presentationRules)

        assertEquals(listOf(2), found.map { it.line })
    }

    @Test
    fun allowsDomainInterfacesHiltComposeHelpersAndSystemObservers() {
        val source = listOf(
            "import androidx.lifecycle.ViewModel",
            "import androidx.hilt.navigation.compose.hiltViewModel",
            "import androidx.compose.runtime.Composable",
            "import android.database.ContentObserver",
            "import dagger.hilt.android.lifecycle.HiltViewModel",
            "import com.example.zar.core.domain.repository.LedgerRepository",
            "import com.example.zar.features.accounts.domain.usecase.ObserveAccountsUseCase",
            "import com.example.zar.core.presentation.theme.ZarTheme",
        )

        assertTrue(LayerBoundaryScanner.scan("DemoScreen.kt", source, LayerBoundaryScanner.presentationRules).isEmpty())
    }

    @Test
    fun ignoresStorageNamesInCommentsAndKdoc() {
        val source = listOf(
            "/**",
            " * Never import com.google.firebase.firestore here; use the repository.",
            " */",
            "// androidx.room.Room is not allowed either",
            "val ok = 1 /* android.database.sqlite.SQLiteDatabase */",
        )

        assertTrue(LayerBoundaryScanner.scan("DemoScreen.kt", source, LayerBoundaryScanner.presentationRules).isEmpty())
    }

    @Test
    fun domainRulesRejectFrameworkStorageAndOuterLayers() {
        val source = listOf(
            "import android.content.Context",
            "import androidx.compose.runtime.Composable",
            "import com.google.firebase.Timestamp",
            "import dagger.hilt.InstallIn",
            "import com.example.zar.core.data.remote.firestore.LedgerDocumentMapper",
            "import com.example.zar.features.home.presentation.HomeViewModel",
            "import javax.inject.Inject",
            "import kotlinx.coroutines.flow.Flow",
        )

        val rules = LayerBoundaryScanner.scan("Demo.kt", source, LayerBoundaryScanner.domainRules).map { it.line to it.rule }

        assertEquals(
            listOf(
                1 to "android-framework",
                2 to "android-framework",
                2 to "compose",
                3 to "firebase-sdk",
                4 to "hilt",
                5 to "data-layer",
                6 to "presentation-layer",
            ),
            rules,
        )
    }

    @Test
    fun domainTestRulesRejectFirebaseAndroidAndRobolectricButAllowLifecycleViewModelScope() {
        val source = listOf(
            "import androidx.lifecycle.viewModelScope",
            "import com.google.firebase.Timestamp",
            "import android.content.Context",
            "import org.robolectric.RobolectricTestRunner",
            "import androidx.test.core.app.ApplicationProvider",
        )

        val rules = LayerBoundaryScanner.scan("Demo.kt", source, LayerBoundaryScanner.domainTestRules).map { it.rule }

        assertEquals(listOf("firebase-sdk", "android-framework", "robolectric", "android-instrumentation"), rules)
    }
}
