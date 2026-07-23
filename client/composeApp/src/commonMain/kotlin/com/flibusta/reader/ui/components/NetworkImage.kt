package com.flibusta.reader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.flibusta.reader.util.decodeImageBitmap
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val imageClient by lazy { HttpClient() }
private val memoryCache = mutableMapOf<String, ImageBitmap>()

@Composable
fun NetworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(url) { mutableStateOf(memoryCache[url]) }
    var failed by remember(url) { mutableStateOf(false) }

    if (bitmap == null && !failed) {
        LaunchedEffect(url) {
            try {
                val bytes: ByteArray = imageClient.get(url).body()
                val decoded = decodeImageBitmap(bytes)
                memoryCache[url] = decoded
                bitmap = decoded
            } catch (_: Exception) {
                failed = true
            }
        }
    }

    when {
        bitmap != null -> Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
        !failed -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}
