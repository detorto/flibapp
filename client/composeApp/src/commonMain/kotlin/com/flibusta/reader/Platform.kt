package com.flibusta.reader

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
