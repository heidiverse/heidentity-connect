package org.heidiverse.connect.sdk.data

import kotlinx.serialization.Serializable
import org.heidiverse.connect.sdk.dpop.DpopKeyRef
import org.heidiverse.connect.sdk.networking.authorization.AuthorizationRequest
import org.heidiverse.connect.sdk.networking.configuration.ProviderMetadata
import org.heidiverse.connect.sdk.pkce.CodeChallenge

@Serializable
data class SavedAuthorizationFlow(
	val authorizationRequest: AuthorizationRequest,
	val codeChallenge: CodeChallenge,
	val providerMetadata: ProviderMetadata,

	/**
	 * The DPoP key the flow was started with, so that a flow restored in another process exchanges its code with the same
	 * key. Only the opaque reference travels here — the private key never leaves the secure element, which matters because
	 * this payload is handed to the app to persist wherever it likes during the App2App flow.
	 */
	val dpopKey: DpopKeyRef? = null,
)
