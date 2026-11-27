package org.heidiverse.connect.sdk.logging

internal class IosEventLoggerFactory : OidcEventLoggerFactory {
	override fun create() = DefaultIosEventLogger()
}