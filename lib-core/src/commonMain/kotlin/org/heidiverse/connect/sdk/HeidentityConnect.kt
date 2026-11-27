package org.heidiverse.connect.sdk

import org.heidiverse.connect.sdk.configuration.OidcConfiguration
import org.heidiverse.connect.sdk.dpop.DpopProofProvider
import org.heidiverse.connect.sdk.logging.OidcEventLogger
import org.heidiverse.connect.sdk.networking.AuthorizationService
import org.koin.core.module.Module

expect class HeidentityConnect {
	companion object {
		fun getEventLogger(): OidcEventLogger

		/**
		 * The DPoP proof provider. Resolved from the dependency graph, so this must only be called after
		 * [initialize] — the HTTP layers therefore look it up lazily, on the first request that needs a proof.
		 */
		fun getDpopProofProvider(): DpopProofProvider

		fun loadModules(modules: List<Module>)
	}

	fun initialize(configuration: OidcConfiguration, authService: AuthorizationService)
}
