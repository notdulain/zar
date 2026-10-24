package com.example.zar.features.export.domain.repository

import com.example.zar.features.export.domain.model.ExportFile

/**
 * Saves an export where the user chose. [location] is the destination the platform handed back
 * from its file picker, kept as text so the domain stays free of Android types.
 */
interface ExportFileWriter {
    /**
     * Throws if the destination cannot be written, so the caller can tell the user. Returns the
     * name the file was saved under, which may differ from [ExportFile.fileName] when the user
     * renamed it in the picker, or null when the destination does not report one.
     */
    suspend fun write(location: String, file: ExportFile): String?
}
