package dev.anilbeesetti.nextplayer.core.model

data class WebdavServer(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 443,
    val path: String = "/",
    val username: String,
    val password: String,
    val useSsl: Boolean = true,
    val allowSelfSigned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val baseUrl: String
        get() {
            val scheme = if (useSsl) "https" else "http"
            val normalizedPath = if (path.startsWith("/")) path else "/$path"
            return "$scheme://$host:$port$normalizedPath"
        }
}
