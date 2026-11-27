package org.heidiverse.connect.sdk.pkce.di

import org.heidiverse.connect.sdk.pkce.CodeVerifierGenerator
import org.koin.core.module.Module
import org.koin.dsl.module

internal fun pkceModule() = module {
	codeChallengeGenerator()
	includes(
		CodeVerifierGenerator.koinModule,
	)
}

internal expect fun Module.codeChallengeGenerator()