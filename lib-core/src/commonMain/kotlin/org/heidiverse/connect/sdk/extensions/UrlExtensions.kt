package org.heidiverse.connect.sdk.extensions

import com.eygraber.uri.Builder

internal fun Builder.appendOptionalQueryParameter(name: String, value: String?): Builder {
	return if (value != null) appendQueryParameter(name, value) else this
}