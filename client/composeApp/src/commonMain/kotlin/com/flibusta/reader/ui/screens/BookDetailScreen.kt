package com.flibusta.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flibusta.reader.LocalBookRepository
import com.flibusta.reader.LocalFileSaver
import com.flibusta.reader.data.model.BookResult
import com.flibusta.reader.ui.components.LocalImageUrlResolver
import com.flibusta.reader.ui.components.NetworkImage
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BookDetailScreen(
    private val bookJson: String
) : Screen {

    override val key: ScreenKey = "BookDetail_${bookJson.hashCode()}"

    constructor(book: BookResult) : this(Json.encodeToString(book))

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val book = remember { Json { ignoreUnknownKeys = true }.decodeFromString<BookResult>(bookJson) }
        val navigator = LocalNavigator.currentOrThrow
        val repository = LocalBookRepository.current
        val fileSaver = LocalFileSaver.current
        val imageResolver = LocalImageUrlResolver.current
        val coverUrl = if (book.coverUrl.isNotEmpty()) imageResolver(book.coverUrl) else ""
        val scope = rememberCoroutineScope()

        var downloadingFormat by remember { mutableStateOf<String?>(null) }
        var downloadedFormat by remember { mutableStateOf<String?>(null) }
        var downloadError by remember { mutableStateOf<String?>(null) }
        var savedUri by remember { mutableStateOf("") }
        var savedMimeType by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
            }

            Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                if (coverUrl.isNotEmpty()) {
                    NetworkImage(
                        url = coverUrl,
                        contentDescription = book.title,
                        modifier = Modifier
                            .width(100.dp)
                            .height(150.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (book.authors.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        book.authors.forEach { author ->
                            Text(
                                text = author.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    if (author.id.isNotEmpty()) {
                                        navigator.push(AuthorScreen(author.id, author.name))
                                    }
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (book.year.isNotEmpty()) {
                            CompactBadge(book.year)
                        }
                        if (book.language.isNotEmpty()) {
                            CompactBadge(book.language.uppercase())
                        }
                        book.genres.forEach { genre ->
                            CompactBadge(
                                text = genre,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                onClick = {
                                    navigator.push(GenreBrowseScreen(genre, genre))
                                }
                            )
                        }
                    }
                }
            }

            val formats = book.downloads.filter { it.format != "download" }
            if (formats.isNotEmpty()) {
                val kindleFormat = formats.firstOrNull { it.format == "epub" }
                    ?: formats.firstOrNull { it.format == "mobi" }
                var kindleSending by remember { mutableStateOf(false) }

                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    formats.forEach { dl ->
                        val isDownloading = downloadingFormat == dl.format
                        val isDownloaded = downloadedFormat == dl.format

                        FilledTonalButton(
                            onClick = {
                                if (isDownloading || isDownloaded) return@FilledTonalButton
                                downloadError = null
                                downloadingFormat = dl.format
                                scope.launch {
                                    try {
                                        val bytes = repository.api.downloadBook(book.id, dl.format)
                                        val sanitizedTitle = book.title
                                            .replace(Regex("[^\\w\\s.-]"), "")
                                            .take(50)
                                            .trim()
                                        val fileName = "${sanitizedTitle}.${dl.format}"
                                        val mimeType = mimeTypeForFormat(dl.format)
                                        val result = fileSaver?.saveFile(bytes, fileName, mimeType)
                                        if (result != null && result.success) {
                                            downloadedFormat = dl.format
                                            savedUri = result.uri
                                            savedMimeType = mimeType
                                        } else {
                                            downloadError = "Не удалось сохранить файл"
                                        }
                                    } catch (e: Exception) {
                                        downloadError = e.message ?: "Ошибка скачивания"
                                    } finally {
                                        downloadingFormat = null
                                    }
                                }
                            },
                            enabled = !isDownloading,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            if (isDownloaded) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(dl.format.uppercase(), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (kindleFormat != null) {
                        OutlinedButton(
                            onClick = {
                                if (kindleSending) return@OutlinedButton
                                kindleSending = true
                                downloadError = null
                                scope.launch {
                                    try {
                                        val bytes = repository.api.downloadBook(book.id, kindleFormat.format)
                                        val sanitizedTitle = book.title
                                            .replace(Regex("[^\\w\\s.-]"), "")
                                            .take(50)
                                            .trim()
                                        val fileName = "${sanitizedTitle}.${kindleFormat.format}"
                                        val mimeType = mimeTypeForFormat(kindleFormat.format)
                                        val result = fileSaver?.saveFile(bytes, fileName, mimeType)
                                        if (result != null && result.success) {
                                            fileSaver?.shareToApp(result.uri, mimeType, "com.amazon.kindle")
                                        } else {
                                            downloadError = "Не удалось скачать файл"
                                        }
                                    } catch (e: Exception) {
                                        downloadError = e.message ?: "Ошибка"
                                    } finally {
                                        kindleSending = false
                                    }
                                }
                            },
                            enabled = !kindleSending,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            if (kindleSending) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Kindle", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (downloadedFormat != null) {
                Text(
                    "✓ Сохранено в Загрузки — открыть",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable {
                            if (savedUri.isNotEmpty()) {
                                fileSaver?.openFile(savedUri, savedMimeType)
                            }
                        }
                )
            }
            if (downloadError != null) {
                Text(
                    downloadError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            if (book.description.isNotEmpty()) {
                val cleanDesc = book.description
                    .replace(Regex("<[^>]*>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (cleanDesc.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = cleanDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun mimeTypeForFormat(format: String): String = when (format.lowercase()) {
    "fb2" -> "application/x-fictionbook+xml"
    "epub" -> "application/epub+zip"
    "mobi" -> "application/x-mobipocket-ebook"
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "rtf" -> "application/rtf"
    "html" -> "text/html"
    "djvu" -> "image/vnd.djvu"
    "doc" -> "application/msword"
    else -> "application/octet-stream"
}

@Composable
private fun CompactBadge(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
