package org.heidiverse.connect.sdk

import android.content.Context
import org.heidiverse.connect.sdk.configuration.OidcConfiguration
import org.heidiverse.connect.sdk.di.HeidentityConnectKoinContext
import org.heidiverse.connect.sdk.dpop.DpopProofProvider
import org.heidiverse.connect.sdk.logging.OidcEventLogger
import org.heidiverse.connect.sdk.networking.AuthorizationService
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

actual class HeidentityConnect(private val context: Context) {

	actual companion object {
		actual fun getEventLogger(): OidcEventLogger {
			return HeidentityConnectKoinContext.koin.get<OidcConfiguration>().logger
		}

		actual fun getDpopProofProvider(): DpopProofProvider {
			return HeidentityConnectKoinContext.koin.get<DpopProofProvider>()
		}

		actual fun loadModules(modules: List<Module>) {
			HeidentityConnectKoinContext.koin.loadModules(modules)
		}
	}

	actual fun initialize(configuration: OidcConfiguration, authService: AuthorizationService) {
		HeidentityConnectKoinContext.initialize(
			configuration = configuration,
			authService = authService,
		) {
			androidContext(context)
		}
	}

}
