package org.heidiverse.connect.sdk.configuration

import org.heidiverse.connect.sdk.di.HeidentityConnectKoinComponent
import org.heidiverse.connect.sdk.logging.OidcEventLogger
import org.heidiverse.connect.sdk.storage.AuthStorage
import org.koin.core.component.get

open class DefaultOidcConfiguration : OidcConfiguration, HeidentityConnectKoinComponent {

	override val authStorage: AuthStorage
		get() = get<AuthStorage>()

	override val logger: OidcEventLogger
		get() = get<OidcEventLogger>()

}