package com.flibusta.reader.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme { LIGHT, DARK, SYSTEM }

class SettingsRepository {
    companion object {
        const val DEFAULT_BASE_URL = "https://flibapp.xyz"
    }

    private val _baseUrl = MutableStateFlow(DEFAULT_BASE_URL)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _theme = MutableStateFlow(AppTheme.SYSTEM)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun setBaseUrl(url: String) {
        _baseUrl.value = url.trimEnd('/')
    }

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
    }
}
