package com.flibusta.reader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flibusta.reader.data.model.BookResult

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookCard(
    book: BookResult,
    onCardClick: () -> Unit,
    onAuthorClick: (authorId: String, authorName: String) -> Unit,
    onGenreClick: (genre: String) -> Unit,
    onDownloadClick: (bookId: String, format: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageResolver = LocalImageUrlResolver.current
    val coverUrl = if (book.coverUrl.isNotEmpty()) imageResolver(book.coverUrl) else ""

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onCardClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (coverUrl.isNotEmpty()) {
                NetworkImage(
                    url = coverUrl,
                    contentDescription = book.title,
                    modifier = Modifier
                        .width(48.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (book.authors.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = book.authors.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            book.authors.firstOrNull { it.id.isNotEmpty() }?.let { a ->
                                onAuthorClick(a.id, a.name)
                            }
                        }
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (book.year.isNotEmpty()) {
                        TagBadge(text = book.year)
                    }
                    if (book.language.isNotEmpty()) {
                        TagBadge(text = book.language.uppercase())
                    }
                    book.genres.take(2).forEach { genre ->
                        TagBadge(
                            text = genre,
                            onClick = { onGenreClick(genre) },
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagBadge(
    text: String,
    onClick: (() -> Unit)? = null,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer
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
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .widthIn(max = 140.dp)
        )
    }
}
