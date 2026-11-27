package org.heidiverse.connect.sdk.di

import org.heidiverse.connect.sdk.dpop.di.dpopModule
import org.heidiverse.connect.sdk.logging.di.loggingModule
import org.heidiverse.connect.sdk.networking.di.jsonModule
import org.heidiverse.connect.sdk.pkce.di.pkceModule
import org.heidiverse.connect.sdk.repository.AuthorizationRepository
import org.heidiverse.connect.sdk.storage.di.storageModule
import org.koin.core.KoinApplication

internal fun KoinApplication.oidcModules() {
	modules(
		jsonModule(),
		loggingModule(),
		storageModule(),
		pkceModule(),
		dpopModule(),
		AuthorizationRepository.koinModule,
	)
}