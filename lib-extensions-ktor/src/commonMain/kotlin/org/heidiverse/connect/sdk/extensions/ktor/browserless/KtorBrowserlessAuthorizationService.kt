package org.heidiverse.connect.sdk.extensions.ktor.browserless

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import org.heidiverse.connect.sdk.exception.HeidentityConnectException
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessErrorType
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessException
import org.heidiverse.connect.sdk.extensions.browserless.networking.BrowserlessAuthorizationResponse
import org.heidiverse.connect.sdk.extensions.browserless.networking.BrowserlessAuthorizationService
import org.heidiverse.connect.sdk.extensions.ktor.extensions.fromNetworkCall

class KtorBrowserlessAuthorizationService(
    private val httpClient: HttpClient
) : BrowserlessAuthorizationService {
    override suspend fun authorize(url: String, customHeaders: Map<String, String>): BrowserlessAuthorizationResponse {
        try {
            val response = httpClient.get(url) {
                header(HttpHeaders.Accept, ContentType.Application.Json)

                for ((key, value) in customHeaders) {
                    header(key, value)
                }
            }
            return response.body<BrowserlessAuthorizationResponse>()
        } catch (e: Exception) {
            throw HeidentityConnectException.fromNetworkCall(e, BrowserlessErrorType.AUTHORIZATION)
        }
    }

    override fun getHostName(url: String): String {
        try {
            return Url(url).host
        } catch (e: Exception) {
            throw BrowserlessException(
                reason = BrowserlessException.Reason.MALFORMED_URL,
                message = "Malformed URL: $url",
                cause = e
            )
        }
    }
}