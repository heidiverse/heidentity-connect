package org.heidiverse.connect.sdk.extensions

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import java.io.Serializable

@Suppress("DEPRECATION")
inline fun <reified T : Parcelable> Bundle.optionalParcelable(
	key: String,
) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
	getParcelable(key, T::class.java)
} else {
	getParcelable(key)
}

@Suppress("DEPRECATION")
inline fun <reified T : Serializable> Bundle.optionalSerializable(
	key: String,
) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
	getSerializable(key, T::class.java)
} else {
	getSerializable(key) as T?
}

inline fun <reified T : Parcelable> Bundle.requireParcelable(key: String) = requireNotNull(optionalParcelable<T>(key))

inline fun <reified T : Serializable> Bundle.requireSerializable(key: String) = requireNotNull(optionalSerializable<T>(key))

fun Bundle.requireString(key: String) = requireNotNull(getString(key))
