package org.heidiverse.connect.sdk.sample.shared

/**
 * Persists the App2App login in progress while the user is in the user-interacting app.
 *
 * The OS may kill the sample app in the meantime, and the callback then arrives in a new process. The saved flow
 * contains everything needed to finish the login there: the PKCE code verifier, the state, the cookies of the
 * authorization server and, with DPoP, the reference to the key.
 *
 * Stored in plain app-private storage, which is enough for a sample app. A real app may want to encrypt it.
 */
expect class App2AppFlowStorage {
	/** The serialized flow, or null if no App2App login is in progress */
	var savedAuthFlow: String?
}
