package com.example.zar.features.export.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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

    override suspend fun write(location: String, file: ExportFile): String? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(location)
        // "wt" truncates, so saving over an existing file never leaves old bytes at the end.
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not open the destination")
        stream.use {
            it.write(UTF8_BOM)
            it.write(file.csv.toByteArray(Charsets.UTF_8))
        }
        displayName(uri)
    }

    /** The name the picker saved under; the user may have renamed the suggested one. */
    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private companion object {
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
