package com.example.zar.features.export.domain.model

/** The two exports. They are always separate files so received and expected money never mix. */
enum class ExportKind { TRANSACTIONS, EXPECTED_PAYMENTS }

/** A finished export, ready to be saved to a location the user picks. */
data class ExportFile(
    val kind: ExportKind,
    val fileName: String,
    val csv: String,
    val rowCount: Int,
)

/** How many rows each export would hold, shown before the user exports. */
data class ExportCounts(
    val transactions: Int,
    val expectedPayments: Int,
) {
    fun of(kind: ExportKind): Int = when (kind) {
        ExportKind.TRANSACTIONS -> transactions
        ExportKind.EXPECTED_PAYMENTS -> expectedPayments
    }
}
