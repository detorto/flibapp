package com.flibusta.reader

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.flibusta.reader.util.FileSaver

fun main() = application {
    val fileSaver = FileSaver()
    Window(
        onCloseRequest = ::exitApplication,
        title = "FlibApp",
        state = rememberWindowState(width = 420.dp, height = 800.dp)
    ) {
        CompositionLocalProvider(LocalFileSaver provides fileSaver) {
            App()
        }
    }
}
