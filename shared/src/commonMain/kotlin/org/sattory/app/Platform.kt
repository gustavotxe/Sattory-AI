package org.sattory.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform