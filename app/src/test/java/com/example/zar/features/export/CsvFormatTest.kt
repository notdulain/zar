package com.example.zar.features.export

import com.example.zar.features.export.domain.Csv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvFormatTest {

    @Test
    fun plainTextIsLeftAlone() {
        assertEquals("Keells Super", Csv.cell("Keells Super"))
        assertEquals("1250.50", Csv.cell("1250.50"))
        assertEquals("2026-10-24", Csv.cell("2026-10-24"))
    }

    @Test
    fun emptyAndMissingCellsAreEmpty() {
        assertEquals("", Csv.cell(null))
        assertEquals("", Csv.cell(""))
    }

    @Test
    fun commasQuotesAndLineBreaksAreQuoted() {
        assertEquals("\"Lunch, rice\"", Csv.cell("Lunch, rice"))
        assertEquals("\"She said \"\"hi\"\"\"", Csv.cell("She said \"hi\""))
        assertEquals("\"line one\nline two\"", Csv.cell("line one\nline two"))
        assertEquals("\"a\r\nb\"", Csv.cell("a\r\nb"))
    }

    @Test
    fun textThatCouldRunAsAFormulaIsNeutralised() {
        assertEquals("'=SUM(A1:A9)", Csv.cell("=SUM(A1:A9)"))
        assertEquals("'+94771234567", Csv.cell("+94771234567"))
        assertEquals("'-cmd", Csv.cell("-cmd"))
        assertEquals("'@handle", Csv.cell("@handle"))
        assertEquals("\"'=1+1, 2\"", Csv.cell("=1+1, 2"))
    }

    @Test
    fun aDashInsideTextIsNotChanged() {
        assertEquals("Dinner - fried rice", Csv.cell("Dinner - fried rice"))
    }

    @Test
    fun sinhalaAndTamilTextSurvives() {
        assertEquals("කෑම", Csv.cell("කෑම"))
        assertEquals("உணவு", Csv.cell("உணவு"))
    }

    @Test
    fun rowsEndWithCrlfAndCountOnlyDataRows() {
        val doc = Csv.document(listOf("a", "b"), listOf(listOf("1", "x, y"), listOf("2", null)))
        assertEquals("a,b\r\n1,\"x, y\"\r\n2,\r\n", doc.text)
        assertEquals(2, doc.rowCount)
    }

    @Test
    fun aHeaderOnlyFileHasNoRows() {
        val doc = Csv.document(listOf("a", "b"), emptyList())
        assertEquals("a,b\r\n", doc.text)
        assertEquals(0, doc.rowCount)
    }

    @Test
    fun aRowWithTheWrongNumberOfCellsIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Csv.document(listOf("a", "b"), listOf(listOf("only one")))
        }
    }

    @Test
    fun theTestReaderRoundTripsTrickyCells() {
        val cells = listOf("plain", "with, comma", "with \"quote\"", "two\nlines", "", "=evil")
        val parsed = parseCsv(Csv.document(cells.indices.map { "c$it" }, listOf(cells)).text)
        assertEquals(listOf("plain", "with, comma", "with \"quote\"", "two\nlines", "", "'=evil"), parsed[1])
    }
}
