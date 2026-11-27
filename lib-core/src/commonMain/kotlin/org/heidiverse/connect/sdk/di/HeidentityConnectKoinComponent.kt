package org.heidiverse.connect.sdk.di

import org.koin.core.component.KoinComponent

interface HeidentityConnectKoinComponent : KoinComponent {
	override fun getKoin() = HeidentityConnectKoinContext.koin
}
