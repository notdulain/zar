package com.example.zar.features.export.data

import android.content.Context
import android.net.Uri
import com.example.zar.features.export.domain.model.ExportFile
import com.example.zar.features.export.domain.repository.ExportFileWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes the CSV to the document the user created in the system file picker, so the app needs no
 * storage permission. The file is UTF-8 with a byte-order mark so Excel reads Sinhala and Tamil
 * text in notes and merchants correctly.
 */
@Singleton
class ContentResolverExportFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExportFileWriter {

    override suspend fun write(location: String, file: ExportFile) = withContext(Dispatchers.IO) {
        // "wt" truncates, so saving over an existing file never leaves old bytes at the end.
        val stream = context.contentResolver.openOutputStream(Uri.parse(location), "wt")
            ?: throw IOException("Could not open the destination")
        stream.use {
            it.write(UTF8_BOM)
            it.write(file.csv.toByteArray(Charsets.UTF_8))
        }
    }

    private companion object {
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
