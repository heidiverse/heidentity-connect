package org.heidiverse.connect.sdk.logging.di

import org.heidiverse.connect.sdk.logging.OidcEventLogger
import org.heidiverse.connect.sdk.logging.OidcEventLoggerFactory
import org.koin.core.module.Module
import org.koin.dsl.module

internal fun loggingModule() = module {
	loggerFactory()
	single<OidcEventLogger> { get<OidcEventLoggerFactory>().create() }
}

internal expect fun Module.loggerFactory()