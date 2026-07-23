package com.flibusta.reader.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

actual class FileSaver(private val context: Context) {
    actual suspend fun saveFile(bytes: ByteArray, fileName: String, mimeType: String): SaveResult {
        return try {
            val content = unzipIfNeeded(bytes, fileName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return SaveResult(false)
                context.contentResolver.openOutputStream(uri)?.use { it.write(content) }
                SaveResult(true, uri.toString())
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(content) }
                SaveResult(true, Uri.fromFile(file).toString())
            }
        } catch (_: Exception) {
            SaveResult(false)
        }
    }

    actual fun openFile(uri: String, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uri), mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uri), "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    actual fun shareToApp(uri: String, mimeType: String, targetPackage: String) {
        val packageName = targetPackage
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage(packageName)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(intent, "Отправить в...").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
        }
    }
}

private val UNZIP_EXTENSIONS = setOf("fb2", "txt", "rtf", "html", "htm")

private fun unzipIfNeeded(bytes: ByteArray, fileName: String): ByteArray {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext !in UNZIP_EXTENSIONS) return bytes
    if (bytes.size < 4 || bytes[0] != 0x50.toByte() || bytes[1] != 0x4B.toByte() ||
        bytes[2] != 0x03.toByte() || bytes[3] != 0x04.toByte()) {
        return bytes
    }
    return try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            val entry = zis.nextEntry ?: return bytes
            val out = ByteArrayOutputStream()
            zis.copyTo(out)
            out.toByteArray()
        }
    } catch (_: Exception) {
        bytes
    }
}
