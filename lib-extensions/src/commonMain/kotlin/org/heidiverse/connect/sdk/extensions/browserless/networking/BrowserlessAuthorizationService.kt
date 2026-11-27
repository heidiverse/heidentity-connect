package org.heidiverse.connect.sdk.extensions.browserless.networking

interface BrowserlessAuthorizationService {
    suspend fun authorize(url: String, customHeaders: Map<String, String> = emptyMap()): BrowserlessAuthorizationResponse

    fun getHostName(url: String): String
}