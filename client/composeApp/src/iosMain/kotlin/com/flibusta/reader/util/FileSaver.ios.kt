package com.flibusta.reader.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.allocArrayOf
import platform.Foundation.*

actual class FileSaver {
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun saveFile(bytes: ByteArray, fileName: String, mimeType: String): SaveResult {
        return try {
            val docs = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory, NSUserDomainMask, true
            ).firstOrNull() as? String ?: return SaveResult(false)
            val path = "$docs/$fileName"
            val data = bytes.toNSData()
            data.writeToFile(path, atomically = true)
            SaveResult(true, path)
        } catch (_: Exception) {
            SaveResult(false)
        }
    }

    actual fun openFile(uri: String, mimeType: String) {
    }

    actual fun shareToApp(uri: String, mimeType: String, targetPackage: String) {
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(
        bytes = allocArrayOf(this@toNSData),
        length = this@toNSData.size.toULong()
    )
}
