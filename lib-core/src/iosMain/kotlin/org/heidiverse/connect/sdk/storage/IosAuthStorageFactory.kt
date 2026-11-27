package org.heidiverse.connect.sdk.storage

import kotlinx.serialization.json.Json

internal class IosAuthStorageFactory(
	private val json: Json,
) : AuthStorageFactory {

	override fun createDefault() = IosKeychainAuthStorage(json)

}