package com.flibusta.reader.util

data class SaveResult(val success: Boolean, val uri: String = "")

expect class FileSaver {
    suspend fun saveFile(bytes: ByteArray, fileName: String, mimeType: String): SaveResult
    fun openFile(uri: String, mimeType: String)
    fun shareToApp(uri: String, mimeType: String, targetPackage: String)
}
