package com.flibusta.reader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flibusta.reader.LocalBookRepository
import com.flibusta.reader.ui.components.AuthorScreenModel
import com.flibusta.reader.ui.components.BookCard

class AuthorScreen(
    private val authorId: String,
    private val authorName: String
) : Screen {

    override val key: ScreenKey = "AuthorScreen_$authorId"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = LocalBookRepository.current
        val model = rememberScreenModel { AuthorScreenModel(authorId, repository) }
        val state by model.state.collectAsState()

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    authorName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = model::load) { Text("Повторить") }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val author = state.author
                        if (author != null && author.bio.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "Об авторе",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        val cleanBio = author.bio
                                            .replace(Regex("<[^>]*>"), " ")
                                            .replace(Regex("\\s+"), " ")
                                            .trim()
                                        Text(
                                            text = cleanBio,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 12,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (state.isLoadingBooks) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        } else if (state.booksError != null) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        state.booksError ?: "",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(onClick = model::retryBooks) {
                                        Text("Загрузить книги")
                                    }
                                }
                            }
                        } else if (state.books.isNotEmpty()) {
                            item {
                                Text(
                                    "Книги (${state.books.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(state.books.size, key = { idx -> "${state.books[idx].id}_$idx" }) { idx ->
                                val book = state.books[idx]
                                BookCard(
                                    book = book,
                                    onCardClick = {
                                        navigator.push(BookDetailScreen(book))
                                    },
                                    onAuthorClick = { id, name ->
                                        if (id != authorId) {
                                            navigator.push(AuthorScreen(id, name))
                                        }
                                    },
                                    onGenreClick = { genre ->
                                        navigator.push(GenreBrowseScreen(genre, genre))
                                    },
                                    onDownloadClick = { _, _ -> }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
