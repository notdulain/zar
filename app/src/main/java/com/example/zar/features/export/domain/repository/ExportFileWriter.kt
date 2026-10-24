package com.example.zar.features.export.domain.repository

import com.example.zar.features.export.domain.model.ExportFile

/**
 * Saves an export where the user chose. [location] is the destination the platform handed back
 * from its file picker, kept as text so the domain stays free of Android types.
 */
interface ExportFileWriter {
    /** Throws if the destination cannot be written, so the caller can tell the user. */
    suspend fun write(location: String, file: ExportFile)
}
