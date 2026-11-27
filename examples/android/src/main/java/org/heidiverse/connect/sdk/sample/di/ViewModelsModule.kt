package org.heidiverse.connect.sdk.sample.di

import org.heidiverse.connect.sdk.sample.feature.oidc.OidcViewModel
import org.koin.dsl.module

fun viewModelsModule() = module {
	includes(
		OidcViewModel.koinModule,
	)
}