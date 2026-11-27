package org.heidiverse.connect.sdk.ktor

import io.ktor.client.HttpClient
import org.heidiverse.connect.sdk.configuration.DefaultOidcConfiguration
import org.heidiverse.connect.sdk.configuration.HttpClientConfiguration
import org.heidiverse.connect.sdk.configuration.OidcConfiguration

expect class HeidentityConnectKtor {
	fun initialize(
		configuration: OidcConfiguration = DefaultOidcConfiguration(),
		httpClientConfiguration: HttpClientConfiguration = HttpClientConfiguration(),
	)

	fun initialize(
		configuration: OidcConfiguration = DefaultOidcConfiguration(),
		httpClient: HttpClient,
	)
}