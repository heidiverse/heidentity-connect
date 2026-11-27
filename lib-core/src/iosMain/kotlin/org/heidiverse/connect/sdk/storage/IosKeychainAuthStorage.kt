package org.heidiverse.connect.sdk.storage

import kotlinx.cinterop.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import org.heidiverse.connect.sdk.configuration.OidcConfiguration
import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.di.HeidentityConnectKoinComponent
import org.heidiverse.connect.sdk.logging.OidcEvent
import org.heidiverse.connect.sdk.networking.configuration.ProviderMetadata
import org.koin.core.component.inject
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*
import platform.darwin.OSStatus
import platform.darwin.noErr

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosKeychainAuthStorage(
	private val json: Json
) : AuthStorage, HeidentityConnectKoinComponent {

	private val config by inject<OidcConfiguration>()

	private val authTokensFlow = MutableStateFlow<AuthTokens?>(null)

	init {
		if (hasAuthTokens()) {
			authTokensFlow.value = getCurrentAuthTokens()
		}
	}
	override fun getProviderMetadata(): ProviderMetadata? {
		val raw = getProviderMetadataRaw() ?: return null
		return runCatching { json.decodeFromString<ProviderMetadata>(raw) }.getOrNull()
	}

	override fun getProviderMetadataRaw(): String? {
		return getKeychainItem(AuthStorage.KEY_PROVIDER_METADATA)
	}

	override fun setProviderMetadataRaw(rawMetadata: String?) {
		if (rawMetadata == null) {
			removeKeychainItem(AuthStorage.KEY_PROVIDER_METADATA)
		} else {
			setKeychainItem(AuthStorage.KEY_PROVIDER_METADATA, rawMetadata)
		}
	}

	override fun getCurrentAuthTokens(): AuthTokens? {
		val raw = getKeychainItem(AuthStorage.KEY_AUTH_STATE) ?: return null
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
		return keychainItemExists(AuthStorage.KEY_AUTH_STATE)
	}

	override fun getAuthTokenFlow(): Flow<AuthTokens?> {
		return authTokensFlow
	}

	override fun setAuthTokens(newAuthTokens: AuthTokens?) {
		if (newAuthTokens == null) {
			removeKeychainItem(AuthStorage.KEY_AUTH_STATE)
		} else {
			setKeychainItem(AuthStorage.KEY_AUTH_STATE, json.encodeToString(newAuthTokens))
		}
		authTokensFlow.value = newAuthTokens
	}

	// Helpers

	private fun getKeychainItem(key: String): String? {
		val query = query(
			kSecClass to kSecClassGenericPassword,
			kSecAttrAccount to CFBridgingRetain(key),
			kSecReturnData to kCFBooleanTrue,
			kSecMatchLimit to kSecMatchLimitOne
		)

		memScoped {
			val result = alloc<CFTypeRefVar>()
			val status = SecItemCopyMatching(query, result.ptr)
			return if (status.validate()) {
				val data = CFBridgingRelease(result.value) as? NSData
				data?.stringValue
			} else {
				config.logger.logEvent(OidcEvent.KeychainRetrievalError(key, status))
				null
			}
		}
	}

	private fun setKeychainItem(key: String, value: String) {
		removeKeychainItem(key)

		val data = value.toNSData()
		val query = query(
			kSecClass to kSecClassGenericPassword,
			kSecAttrAccount to CFBridgingRetain(key),
			kSecValueData to CFBridgingRetain(data),
			kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
		)

		val status = SecItemAdd(query, null)
		if (!status.validate()) {
			config.logger.logEvent(OidcEvent.KeychainSaveError(key, status))
		}
	}

	private fun removeKeychainItem(key: String) {
		val query = query(
			kSecClass to kSecClassGenericPassword,
			kSecAttrAccount to CFBridgingRetain(key)
		)

		val status = SecItemDelete(query)
		if (status == errSecItemNotFound) {
			return
		}
		if (!status.validate()) {
			config.logger.logEvent(OidcEvent.KeychainDeleteError(key, status))
		}
	}

	private fun keychainItemExists(key: String): Boolean {
		val query = query(
			kSecClass to kSecClassGenericPassword,
			kSecAttrAccount to CFBridgingRetain(key),
			kSecMatchLimit to kSecMatchLimitOne
		)

		val status = SecItemCopyMatching(query, null)
		if (!status.validate() && status != errSecItemNotFound) {
			config.logger.logEvent(OidcEvent.KeychainRetrievalError(key, status))
		}
		return status.validate()
	}

	private fun query(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
		val map = mapOf(*pairs)
		return CFDictionaryCreateMutable(
			null, map.size.convert(), null, null
		).apply {
			map.entries.forEach { CFDictionaryAddValue(this, it.key, it.value) }
		}.apply {
			CFAutorelease(this)
		}
	}

	private fun String.toNSData(): NSData? = NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding)

	private val NSData.stringValue: String?
		get() = NSString.create(this, NSUTF8StringEncoding) as String?

	private fun OSStatus.validate(): Boolean = toUInt() == noErr
}
