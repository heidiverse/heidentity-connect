package org.heidiverse.connect.sdk.ktor

import android.content.Context
import io.ktor.client.HttpClient
import org.heidiverse.connect.sdk.HeidentityConnect
import org.heidiverse.connect.sdk.configuration.HttpClientConfiguration
import org.heidiverse.connect.sdk.configuration.OidcConfiguration
import org.heidiverse.connect.sdk.ktor.networking.KtorAuthorizationService
import org.heidiverse.connect.sdk.ktor.networking.KtorHttpClientFactory

actual class HeidentityConnectKtor(private val context: Context) {

	actual fun initialize(
		configuration: OidcConfiguration,
		httpClientConfiguration: HttpClientConfiguration,
	) {
		val httpClient = KtorHttpClientFactory(httpClientConfiguration).create()
		initialize(configuration, httpClient)
	}

	actual fun initialize(
		configuration: OidcConfiguration,
		httpClient: HttpClient,
	) {
		val authService = KtorAuthorizationService(httpClient)
		HeidentityConnect(context).initialize(configuration, authService)
	}

}