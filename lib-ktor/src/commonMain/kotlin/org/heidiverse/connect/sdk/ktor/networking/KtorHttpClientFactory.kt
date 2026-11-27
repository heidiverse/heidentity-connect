package org.heidiverse.connect.sdk.ktor.networking

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.heidiverse.connect.sdk.configuration.HttpClientConfiguration

class KtorHttpClientFactory(
	private val httpClientConfiguration: HttpClientConfiguration,
) {

	fun create(cookieStore: CookiesStorage? = null): HttpClient {
		val json = Json {
			encodeDefaults = true
			explicitNulls = false
			ignoreUnknownKeys = true
		}

		return HttpClient {
			expectSuccess = true
			install(HttpCache) // TODO UBMW: Replace with Ubiquache or at least a persistent file storage cache
			install(ContentNegotiation) {
				json(json)
			}
			install(HttpTimeout) {
				requestTimeoutMillis = httpClientConfiguration.requestTimeoutMillis
			}
			install(UserAgent) {
				agent = httpClientConfiguration.userAgent
			}
			cookieStore?.let {
				install(HttpCookies) {
                    storage = it
                }
            }
		}
	}

}