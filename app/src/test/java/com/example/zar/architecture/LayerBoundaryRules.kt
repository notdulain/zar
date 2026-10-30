package com.example.zar.architecture

import java.io.File

/** One forbidden dependency found in a source file. */
data class BoundaryViolation(
    val path: String,
    val line: Int,
    val rule: String,
    val source: String,
) {
    override fun toString(): String = "$path:$line [$rule] $source"
}

/** A named pattern that must not appear in a layer's source. [import] limits it to import lines. */
class BoundaryRule(
    val name: String,
    val pattern: Regex,
    val importOnly: Boolean = false,
    /** Matches that are legitimate for this rule, such as a system-settings observer. */
    val allowed: List<Regex> = emptyList(),
)

/**
 * Source-text scanner shared by the architecture tests. It looks at imports and at fully
 * qualified names in code, skips comments, and reports `path:line` so a regression points at
 * the offending line.
 */
object LayerBoundaryScanner {

    private const val ZAR = "com\\.example\\.zar"

    /** Storage SDKs and drivers: a screen or ViewModel must never touch these. */
    private val storageSdks = listOf(
        BoundaryRule("firebase-sdk", Regex("\\bcom\\.google\\.firebase\\.")),
        BoundaryRule("firestore-sdk", Regex("\\bcom\\.google\\.cloud\\.firestore\\.")),
        BoundaryRule("room", Regex("\\bandroidx\\.room\\.")),
        BoundaryRule("androidx-sqlite", Regex("\\bandroidx\\.sqlite\\.")),
        BoundaryRule("android-sqlite", Regex("\\bandroid\\.database\\.sqlite\\.")),
        BoundaryRule("android-cursor", Regex("\\bandroid\\.database\\.Cursor\\b")),
        BoundaryRule("sqlcipher", Regex("\\bnet\\.(sqlcipher|zetetic)\\.")),
    )

    /** Screens, ViewModels and navigation: storage SDKs, concrete data code and Hilt modules are off limits. */
    val presentationRules: List<BoundaryRule> = storageSdks + listOf(
        BoundaryRule("concrete-data-layer", Regex("\\b$ZAR\\.(?:[a-z0-9_]+\\.)*data\\.")),
        BoundaryRule("hilt-wiring-module", Regex("\\b$ZAR\\.di\\.")),
    )

    /** Domain code: no framework, no storage, and no dependency on the layers around it. */
    val domainRules: List<BoundaryRule> = storageSdks + listOf(
        BoundaryRule("android-framework", Regex("\\bandroid(x)?\\.")),
        BoundaryRule("hilt", Regex("\\bdagger\\.")),
        BoundaryRule("compose", Regex("\\bandroidx\\.compose\\.")),
        BoundaryRule("data-layer", Regex("\\b$ZAR\\.(?:[a-z0-9_]+\\.)*data\\.")),
        BoundaryRule("presentation-layer", Regex("\\b$ZAR\\.(?:[a-z0-9_]+\\.)*presentation\\.")),
        BoundaryRule("hilt-wiring-module", Regex("\\b$ZAR\\.di\\.")),
    )

    /** Data code implements domain interfaces; it must not reach up into screens or ViewModels. */
    val dataRules: List<BoundaryRule> = listOf(
        BoundaryRule("presentation-layer", Regex("\\b$ZAR\\.(?:[a-z0-9_]+\\.)*presentation\\.")),
    )

    /** Domain JVM tests prove calculations run without Android, Robolectric or Firebase. */
    val domainTestRules: List<BoundaryRule> = listOf(
        BoundaryRule("firebase-sdk", Regex("\\bcom\\.google\\.firebase\\.")),
        BoundaryRule("android-framework", Regex("^android\\."), importOnly = true),
        BoundaryRule("android-instrumentation", Regex("\\bandroidx\\.test\\.")),
        BoundaryRule("robolectric", Regex("\\borg\\.robolectric\\.")),
    )

    fun scan(path: String, lines: List<String>, rules: List<BoundaryRule>): List<BoundaryViolation> {
        val found = mutableListOf<BoundaryViolation>()
        var inBlockComment = false
        lines.forEachIndexed { index, raw ->
            val code = stripComments(raw, inBlockComment).also { inBlockComment = it.second }.first
            val text = code.trim()
            if (text.isEmpty() || text.startsWith("package ")) return@forEachIndexed
            val isImport = text.startsWith("import ")
            val target = if (isImport) text.removePrefix("import ").trim() else text
            for (rule in rules) {
                if (rule.importOnly && !isImport) continue
                if (!rule.pattern.containsMatchIn(target)) continue
                if (rule.allowed.any { it.containsMatchIn(target) }) continue
                found += BoundaryViolation(path, index + 1, rule.name, text)
            }
        }
        return found
    }

    fun scan(file: File, root: File, rules: List<BoundaryRule>): List<BoundaryViolation> =
        scan(file.relativeTo(root).path, file.readLines(), rules)

    /** Removes `//` and block comments from [line]; returns the code and whether a block comment is still open. */
    private fun stripComments(line: String, inBlock: Boolean): Pair<String, Boolean> {
        val out = StringBuilder()
        var block = inBlock
        var i = 0
        var inString = false
        while (i < line.length) {
            val c = line[i]
            val next = line.getOrNull(i + 1)
            when {
                block -> if (c == '*' && next == '/') { block = false; i++ }
                inString -> { out.append(c); if (c == '"' && line.getOrNull(i - 1) != '\\') inString = false }
                c == '"' -> { inString = true; out.append(c) }
                c == '/' && next == '/' -> return out.toString() to block
                c == '/' && next == '*' -> { block = true; i++ }
                else -> out.append(c)
            }
            i++
        }
        return out.toString() to block
    }

    /** Production Kotlin under `src/main/java/com/example/zar`. */
    fun productionRoot(): File = File("src/main/java/com/example/zar").also {
        check(it.isDirectory) { "Source root not found: ${it.absolutePath}" }
    }

    fun testRoot(): File = File("src/test/java/com/example/zar").also {
        check(it.isDirectory) { "Test root not found: ${it.absolutePath}" }
    }

    fun kotlinFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()

    private fun inLayer(file: File, layer: String): Boolean =
        "${File.separator}$layer${File.separator}" in file.path

    /** Screens, ViewModels and navigation, which sit above the domain layer. */
    fun isPresentation(file: File): Boolean =
        inLayer(file, "presentation") ||
            inLayer(file, "navigation") ||
            file.name == "MainActivity.kt"

    fun isDomain(file: File): Boolean = inLayer(file, "domain")

    fun isData(file: File): Boolean = inLayer(file, "data")
}
