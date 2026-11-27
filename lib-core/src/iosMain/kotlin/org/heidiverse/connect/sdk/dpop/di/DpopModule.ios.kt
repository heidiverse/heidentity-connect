package org.heidiverse.connect.sdk.dpop.di

import org.heidiverse.connect.sdk.dpop.DpopKeyStore
import org.heidiverse.connect.sdk.dpop.SecureEnclaveDpopKeyStore
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind

internal actual fun Module.dpopKeyStore() {
	singleOf(::SecureEnclaveDpopKeyStore).bind<DpopKeyStore>()
}
