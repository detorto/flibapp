package com.flibusta.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.flibusta.reader.LocalSettingsRepository
import com.flibusta.reader.data.repository.AppTheme

class SettingsScreen : Screen {

    @Composable
    override fun Content() {
        val settings = LocalSettingsRepository.current
        val baseUrl by settings.baseUrl.collectAsState()
        val theme by settings.theme.collectAsState()
        var urlInput by remember { mutableStateOf(baseUrl) }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column {
                Text(
                    "Адрес сервера",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Измените, если текущий сервер заблокирован",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("https://your-backend.example.com") }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { settings.setBaseUrl(urlInput) },
                    enabled = urlInput.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            }

            HorizontalDivider()

            Column {
                Text(
                    "Тема",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                AppTheme.entries.forEach { option ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = theme == option,
                            onClick = { settings.setTheme(option) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (option) {
                                AppTheme.LIGHT -> "Светлая"
                                AppTheme.DARK -> "Тёмная"
                                AppTheme.SYSTEM -> "Системная"
                            },
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            Column {
                val uriHandler = LocalUriHandler.current
                Text(
                    "FlibApp",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Поиск и скачивание книг из каталога Флибусты",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "flibapp.xyz",
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDecoration = TextDecoration.Underline
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://flibapp.xyz")
                    }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Исходный код",
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDecoration = TextDecoration.Underline
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://flibapp.xyz")
                    }
                )
            }
        }
    }
}
