package org.heidiverse.connect.sdk.extensions.okhttp.browserless

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.heidiverse.connect.sdk.exception.HeidentityConnectException
import org.heidiverse.connect.sdk.exception.ResponseException
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessErrorType
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessException
import org.heidiverse.connect.sdk.extensions.browserless.networking.BrowserlessAuthorizationResponse
import org.heidiverse.connect.sdk.extensions.browserless.networking.BrowserlessAuthorizationService
import org.heidiverse.connect.sdk.extensions.okhttp.extensions.fromNetworkCall

class OkHttpBrowserlessAuthorizationService(
    private val httpClient: OkHttpClient,
    private val json: Json,
) : BrowserlessAuthorizationService {
    override suspend fun authorize(url: String, customHeaders: Map<String, String>): BrowserlessAuthorizationResponse {
        try {
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .get()
                .apply {
                    for ((key, value) in customHeaders) {
                        addHeader(key, value)
                    }
                }
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body.string()
                if (!response.isSuccessful) {
                    val message = "Server returned error 'HTTP ${response.code}' for ${response.request.method} ${response.request.url.encodedPath}"
                    throw ResponseException(
                        BrowserlessErrorType.AUTHORIZATION,
                        response.code,
                        message,
                        headers = response.headers.toMultimap(),
                        body = responseBody,
                    )
                }
                return json.decodeFromString<BrowserlessAuthorizationResponse>(responseBody)
            }
        } catch (e: Exception) {
            throw HeidentityConnectException.fromNetworkCall(e, BrowserlessErrorType.AUTHORIZATION)
        }
    }

    override fun getHostName(url: String): String {
        try {
            return url.toHttpUrl().host
        } catch (e: Exception) {
            throw BrowserlessException(
                reason = BrowserlessException.Reason.MALFORMED_URL,
                message = "Malformed URL: $url",
                cause = e
            )
        }
    }
}