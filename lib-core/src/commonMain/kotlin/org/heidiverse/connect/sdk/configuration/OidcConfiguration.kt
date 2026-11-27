package org.heidiverse.connect.sdk.configuration

import org.heidiverse.connect.sdk.logging.OidcEventLogger
import org.heidiverse.connect.sdk.storage.AuthStorage

interface OidcConfiguration {
	val authStorage: AuthStorage
	val logger: OidcEventLogger
}