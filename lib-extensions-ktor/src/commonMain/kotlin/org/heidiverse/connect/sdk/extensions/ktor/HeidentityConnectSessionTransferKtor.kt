package org.heidiverse.connect.sdk.extensions.ktor

import org.heidiverse.connect.sdk.HeidentityConnect
import org.heidiverse.connect.sdk.configuration.HttpClientConfiguration
import org.heidiverse.connect.sdk.extensions.ktor.sessiontransfer.KtorSessionTransferService
import org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.SessionTransferService
import org.heidiverse.connect.sdk.ktor.networking.KtorHttpClientFactory
import org.koin.dsl.module

object HeidentityConnectSessionTransferKtor {
	fun initialize(
		httpClientConfiguration: HttpClientConfiguration = HttpClientConfiguration(),
	) {
		val httpClient = KtorHttpClientFactory(httpClientConfiguration).create()
		HeidentityConnect.loadModules(
			listOf(
				module {
					single<SessionTransferService> { KtorSessionTransferService(httpClient) }
				}
			)
		)
	}
}
