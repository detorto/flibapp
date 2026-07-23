package com.flibusta.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.flibusta.reader.util.FileSaver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val fileSaver = FileSaver(applicationContext)
        setContent {
            CompositionLocalProvider(LocalFileSaver provides fileSaver) {
                App()
            }
        }
    }
}
