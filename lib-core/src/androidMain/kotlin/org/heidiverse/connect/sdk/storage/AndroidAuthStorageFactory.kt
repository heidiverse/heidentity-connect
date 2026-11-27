package org.heidiverse.connect.sdk.storage

import android.content.Context
import kotlinx.serialization.json.Json

internal class AndroidAuthStorageFactory(
	private val context: Context,
	private val json: Json,
) : AuthStorageFactory {

	override fun createDefault() = AndroidPreferencesAuthStorage(context, json)

}