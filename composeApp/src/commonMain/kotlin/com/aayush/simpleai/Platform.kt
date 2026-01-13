package com.aayush.simpleai

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform