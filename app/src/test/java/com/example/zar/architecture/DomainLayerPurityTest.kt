package com.example.zar.architecture

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fails the build if any `domain` package imports Android, Firebase, Compose, Room or Hilt. */
class DomainLayerPurityTest {

    private val forbiddenImports = listOf(
        "android.",
        "androidx.",
        "com.google.firebase.",
        "dagger.",
    )

    @Test
    fun domainCodeHasNoFrameworkImports() {
        val sourceRoot = File("src/main/java/com/example/zar")
        assertTrue("Source root not found: ${sourceRoot.absolutePath}", sourceRoot.isDirectory)

        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { "${File.separator}domain${File.separator}" in it.path }
            .flatMap { file ->
                file.readLines()
                    .filter { line -> forbiddenImports.any { line.trim().startsWith("import $it") } }
                    .map { "${file.relativeTo(sourceRoot)}: ${it.trim()}" }
            }
            .toList()

        assertTrue("Domain layer must stay framework-free:\n${violations.joinToString("\n")}", violations.isEmpty())
    }
}
