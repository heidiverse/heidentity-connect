package org.heidiverse.connect.sdk.storage.di

import org.heidiverse.connect.sdk.storage.AuthStorage
import org.heidiverse.connect.sdk.storage.AuthStorageFactory
import org.koin.core.module.Module
import org.koin.dsl.module

internal fun storageModule() = module {
	authStorageFactory()
	single<AuthStorage> { get<AuthStorageFactory>().createDefault() }
}

internal expect fun Module.authStorageFactory()