package org.heidiverse.connect.sdk.storage

import kotlinx.coroutines.flow.Flow
import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.networking.configuration.ProviderMetadata

// TODO UBMW: Split metadata storage from token storage
interface AuthStorage {

	companion object {
		internal const val KEY_PROVIDER_METADATA = "KEY_PROVIDER_METADATA"
		internal const val KEY_AUTH_STATE = "KEY_AUTH_STATE"
	}

	fun getProviderMetadata(): ProviderMetadata?

	fun getProviderMetadataRaw(): String?

	fun setProviderMetadataRaw(rawMetadata: String?)

	fun getCurrentAuthTokens(): AuthTokens?

	fun hasAuthTokens(): Boolean

	fun getAuthTokenFlow(): Flow<AuthTokens?>

	fun setAuthTokens(newAuthTokens: AuthTokens?)

}
