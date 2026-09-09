package com.example.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.CustomVideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object CustomVideoFileManager {

    suspend fun importVideoFile(context: Context, uri: Uri): CustomVideoItem? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var displayName = "video_${System.currentTimeMillis()}.mp4"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        displayName = name
                    }
                }
            }

            val storageDir = File(context.filesDir, "custom_videos")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }

            val id = UUID.randomUUID().toString()
            val safeFileName = "${id}_${displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
            val destFile = File(storageDir, safeFileName)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: return@withContext null

            return@withContext CustomVideoItem(
                id = id,
                name = displayName,
                filePath = destFile.absolutePath,
                dateAdded = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun deleteVideoFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
