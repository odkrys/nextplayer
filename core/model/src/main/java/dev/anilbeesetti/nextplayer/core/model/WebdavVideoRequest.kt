package dev.anilbeesetti.nextplayer.core.model

data class WebdavVideoRequest(
    val url: String,
    val username: String,
    val password: String,
    val allowSelfSigned: Boolean,
)
