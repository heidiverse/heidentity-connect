package org.heidiverse.connect.sdk.storage.di

import org.heidiverse.connect.sdk.storage.AuthStorageFactory
import org.heidiverse.connect.sdk.storage.IosAuthStorageFactory
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind

internal actual fun Module.authStorageFactory() {
	factoryOf(::IosAuthStorageFactory).bind<AuthStorageFactory>()
}