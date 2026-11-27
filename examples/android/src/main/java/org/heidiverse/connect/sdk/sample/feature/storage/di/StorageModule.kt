package org.heidiverse.connect.sdk.sample.feature.storage.di

import org.heidiverse.connect.sdk.sample.shared.App2AppFlowStorage
import org.heidiverse.connect.sdk.sample.shared.IdpConfigStorage
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun storageModule() = module {
	singleOf(::IdpConfigStorage)
	singleOf(::App2AppFlowStorage)
}
