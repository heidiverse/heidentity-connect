package org.heidiverse.connect.sdk.di

import org.heidiverse.connect.sdk.configuration.OidcConfiguration
import org.heidiverse.connect.sdk.networking.AuthorizationService
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal object HeidentityConnectKoinContext {

	private lateinit var koinApp: KoinApplication
	lateinit var koin: Koin

	fun initialize(
		configuration: OidcConfiguration,
		authService: AuthorizationService,
		platformConfiguration: KoinApplication.() -> Unit = {},
	) {
		koinApp = koinApplication {
			oidcModules()
			modules(
				module {
					single<OidcConfiguration> { configuration }
					single<AuthorizationService> { authService }
				}
			)
			platformConfiguration()
		}
		koin = koinApp.koin
	}
}
