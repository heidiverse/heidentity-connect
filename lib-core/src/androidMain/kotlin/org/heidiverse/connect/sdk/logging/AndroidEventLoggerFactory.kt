package org.heidiverse.connect.sdk.logging

internal class AndroidEventLoggerFactory: OidcEventLoggerFactory {
	override fun create() = DefaultAndroidEventLogger()
}