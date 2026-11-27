package org.heidiverse.connect.sdk.logging.di

import org.heidiverse.connect.sdk.logging.AndroidEventLoggerFactory
import org.heidiverse.connect.sdk.logging.OidcEventLoggerFactory
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind

internal actual fun Module.loggerFactory() {
	factoryOf(::AndroidEventLoggerFactory).bind<OidcEventLoggerFactory>()
}