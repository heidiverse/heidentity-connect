package org.heidiverse.connect.sdk.okhttp.networking

import kotlinx.serialization.json.Json

internal class OkHttpJsonFactory {

	fun create(): Json {
		return Json {
			encodeDefaults = true
			explicitNulls = false
			ignoreUnknownKeys = true
		}
	}

}