package com.flibusta.reader.ui.components

import androidx.compose.runtime.compositionLocalOf

val LocalImageUrlResolver = compositionLocalOf<(String) -> String> { { "" } }
