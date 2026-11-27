package org.heidiverse.connect.sdk.okhttp

import android.content.Context
import okhttp3.OkHttpClient
import org.heidiverse.connect.sdk.HeidentityConnect
import org.heidiverse.connect.sdk.configuration.DefaultOidcConfiguration
import org.heidiverse.connect.sdk.configuration.HttpClientConfiguration
import org.heidiverse.connect.sdk.configuration.OidcConfiguration
import org.heidiverse.connect.sdk.okhttp.networking.OkHttpAuthorizationService
import org.heidiverse.connect.sdk.okhttp.networking.OkHttpClientFactory
import org.heidiverse.connect.sdk.okhttp.networking.OkHttpJsonFactory

class HeidentityConnectOkHttp(private val context: Context) {

	fun initialize(
		configuration: OidcConfiguration = DefaultOidcConfiguration(),
		httpClientConfiguration: HttpClientConfiguration = HttpClientConfiguration(),
	) {
		val httpClient = OkHttpClientFactory(httpClientConfiguration).create()
		initialize(configuration, httpClient)
	}

	fun initialize(
		configuration: OidcConfiguration = DefaultOidcConfiguration(),
		httpClient: OkHttpClient,
	) {
		val json = OkHttpJsonFactory().create()
		val authService = OkHttpAuthorizationService(httpClient, json)
		HeidentityConnect(context).initialize(configuration, authService)
	}

}