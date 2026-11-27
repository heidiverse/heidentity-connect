package org.heidiverse.connect.sdk.dpop.di

import org.heidiverse.connect.sdk.configuration.OidcConfiguration
import org.heidiverse.connect.sdk.dpop.DpopClock
import org.heidiverse.connect.sdk.dpop.DpopKeyManager
import org.heidiverse.connect.sdk.dpop.DpopNonceStore
import org.heidiverse.connect.sdk.dpop.DpopProofFactory
import org.heidiverse.connect.sdk.dpop.DpopProofProvider
import org.heidiverse.connect.sdk.repository.AuthorizationRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal fun dpopModule() = module {
	dpopKeyStore()
	single { DpopClock() }
	singleOf(::DpopProofFactory)
	singleOf(::DpopNonceStore)
	single { DpopKeyManager(get(), get<OidcConfiguration>().logger) }
	single {
		val scope = this
		DpopProofProvider(
			proofFactory = get(),
			nonceStore = get(),
			clock = get(),
			logger = get<OidcConfiguration>().logger,
			// Resolved on use: the repository depends on the key manager, so resolving it here would tie the two modules'
			// construction order together
			onKeyMissing = { keyRef -> scope.get<AuthorizationRepository>().onDpopKeyMissing(keyRef) },
		)
	}
}

internal expect fun Module.dpopKeyStore()
