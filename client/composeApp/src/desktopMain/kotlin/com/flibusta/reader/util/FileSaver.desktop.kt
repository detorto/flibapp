package com.flibusta.reader.util

import java.awt.Desktop
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

actual class FileSaver {
    actual suspend fun saveFile(bytes: ByteArray, fileName: String, mimeType: String): SaveResult {
        return try {
            val content = unzipIfNeeded(bytes, fileName)
            val downloads = File(System.getProperty("user.home"), "Downloads")
            downloads.mkdirs()
            val file = File(downloads, fileName)
            file.writeBytes(content)
            SaveResult(true, file.absolutePath)
        } catch (_: Exception) {
            SaveResult(false)
        }
    }

    actual fun shareToApp(uri: String, mimeType: String, targetPackage: String) {
        openFile(uri, mimeType)
    }

    actual fun openFile(uri: String, mimeType: String) {
        try {
            val file = File(uri)
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            }
        } catch (_: Exception) {
            try {
                val file = File(uri)
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browseFileDirectory(file)
                }
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
