package org.heidiverse.connect.sdk.logging.di

import org.heidiverse.connect.sdk.logging.IosEventLoggerFactory
import org.heidiverse.connect.sdk.logging.OidcEventLoggerFactory
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind

internal actual fun Module.loggerFactory() {
	factoryOf(::IosEventLoggerFactory).bind<OidcEventLoggerFactory>()
}