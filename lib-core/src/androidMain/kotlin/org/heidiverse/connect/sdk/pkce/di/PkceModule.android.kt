package org.heidiverse.connect.sdk.pkce.di

import org.heidiverse.connect.sdk.pkce.AndroidCodeChallengeGenerator
import org.heidiverse.connect.sdk.pkce.CodeChallengeGenerator
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind

internal actual fun Module.codeChallengeGenerator() {
	singleOf(::AndroidCodeChallengeGenerator).bind<CodeChallengeGenerator>()
}