package org.heidiverse.connect.sdk.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import org.heidiverse.connect.sdk.configuration.OidcConfiguration
import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.di.HeidentityConnectKoinComponent
import org.heidiverse.connect.sdk.logging.OidcEvent
import org.heidiverse.connect.sdk.networking.configuration.ProviderMetadata
import org.koin.core.component.inject

internal class AndroidPreferencesAuthStorage(
	context: Context,
	private val json: Json,
) : AuthStorage, HeidentityConnectKoinComponent {

	companion object {
		private const val DEFAULT_PREFERENCES_NAME = "auth_preferences"
	}

	private val config by inject<OidcConfiguration>()

	private val preferences = context.getSharedPreferences(DEFAULT_PREFERENCES_NAME, Context.MODE_PRIVATE)

	override fun getProviderMetadata(): ProviderMetadata? {
		val raw = getProviderMetadataRaw() ?: return null
		return runCatching { json.decodeFromString<ProviderMetadata>(raw) }.getOrNull()
	}

	override fun getProviderMetadataRaw(): String? {
		return preferences.getString(AuthStorage.KEY_PROVIDER_METADATA, null)
	}

	override fun setProviderMetadataRaw(rawMetadata: String?) {
		preferences.edit(commit = true) {
			if (rawMetadata == null) {
				remove(AuthStorage.KEY_PROVIDER_METADATA)
			} else {
				putString(AuthStorage.KEY_PROVIDER_METADATA, rawMetadata)
			}
		}
	}

	override fun getCurrentAuthTokens(): AuthTokens? {
		val raw = preferences.getString(AuthStorage.KEY_AUTH_STATE, null) ?: return null
		return try {
			json.decodeFromString<AuthTokens>(raw)
		} catch (e: Exception) {
			// Unreadable tokens can never be used again, so remove them instead of failing on every access
			config.logger.logEvent(OidcEvent.StoredAuthTokensCorrupted(e))
			setAuthTokens(null)
			null
		}
	}

	override fun hasAuthTokens(): Boolean {
		return preferences.contains(AuthStorage.KEY_AUTH_STATE)
	}

	override fun getAuthTokenFlow(): Flow<AuthTokens?> {
		return callbackFlow {
			val callback = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
				if (key == AuthStorage.KEY_AUTH_STATE) {
					trySend(getCurrentAuthTokens())
				}
			}

			// Send initial value
			send(getCurrentAuthTokens())

			preferences.registerOnSharedPreferenceChangeListener(callback)
			awaitClose {
				preferences.unregisterOnSharedPreferenceChangeListener(callback)
			}
		}
	}

	override fun setAuthTokens(newAuthTokens: AuthTokens?) {
		preferences.edit(commit = true) {
			if (newAuthTokens == null) {
				remove(AuthStorage.KEY_AUTH_STATE)
			} else {
				putString(AuthStorage.KEY_AUTH_STATE, json.encodeToString(newAuthTokens))
			}
		}
	}
}
