package org.heidiverse.connect.sdk.networking.di

import kotlinx.serialization.json.Json
import org.koin.dsl.module

internal fun jsonModule() = module {
	single {
		Json {
			encodeDefaults = true
			explicitNulls = false
			ignoreUnknownKeys = true
		}
	}
}