package org.heidiverse.connect.sdk.dpop

import kotlinx.serialization.json.Json
import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.data.SavedAuthorizationFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `AuthTokens`, `IdpConfig` and `SavedAuthorizationFlow` are all persisted (Android preferences / iOS keychain, and in the
 * App2App flow a saved flow even crosses a process boundary). A payload written by the release before DPoP existed has to
 * keep deserializing, otherwise every already-logged-in user is signed out by the update.
 */
class DpopSerializationCompatibilityTest {

	/** Same configuration as the SDK's shared instance in `networking/di/JsonModule.kt`. */
	private val json = Json {
		encodeDefaults = true
		explicitNulls = false
		ignoreUnknownKeys = true
	}

	@Test
	fun authTokensWrittenBeforeDpopStillDeserialize() {
		val legacyJson = """
			{"accessToken":"at","idToken":"it","refreshToken":"rt","expiresAt":1750000000000}
		""".trimIndent()

		val tokens = json.decodeFromString<AuthTokens>(legacyJson)

		assertEquals("at", tokens.accessToken)
		assertNull(tokens.tokenType)
		assertNull(tokens.dpopKey)
		assertFalse(tokens.isDpopBound())
	}

	@Test
	fun authTokensRoundTripWithADpopKey() {
		val tokens = AuthTokens(
			accessToken = "at",
			idToken = "it",
			refreshToken = "rt",
			expiresAt = 1750000000000,
			tokenType = "DPoP",
			dpopKey = DpopKeyRef("${DpopKeyRef.ALIAS_PREFIX}abc", DpopAlgorithm.ES256, isHardwareBacked = true),
		)

		assertEquals(tokens, json.decodeFromString<AuthTokens>(json.encodeToString(tokens)))
	}

	/**
	 * The whole Keycloak refresh-token-only mode hinges on this: the tokens carry a key (so the refresh request proves
	 * possession) while the access token is a plain bearer token.
	 */
	@Test
	fun tokensWithAKeyButABearerTypeAreNotAccessTokenBound() {
		val tokens = AuthTokens(
			accessToken = "at",
			idToken = null,
			refreshToken = "rt",
			expiresAt = null,
			tokenType = "Bearer",
			dpopKey = DpopKeyRef("alias", DpopAlgorithm.ES256, isHardwareBacked = true),
		)

		assertFalse(tokens.isDpopBound())
	}

	@Test
	fun tokenTypeComparisonIsCaseInsensitive() {
		val keyRef = DpopKeyRef("alias", DpopAlgorithm.ES256, isHardwareBacked = true)
		val base = AuthTokens("at", null, "rt", null, dpopKey = keyRef)

		assertTrue(base.copy(tokenType = "DPoP").isDpopBound())
		assertTrue(base.copy(tokenType = "dpop").isDpopBound())
		assertTrue(base.copy(tokenType = "DPOP").isDpopBound())
	}

	/** Without a key there is nothing to sign with, so a `DPoP` token type alone must not turn on the DPoP scheme. */
	@Test
	fun dpopTokenTypeWithoutAKeyIsNotBound() {
		assertFalse(AuthTokens("at", null, "rt", null, tokenType = "DPoP", dpopKey = null).isDpopBound())
	}

	@Test
	fun idpConfigWrittenBeforeDpopStillDeserializes() {
		val legacyJson = """
			{"discoveryUrl":"https://idp.example.com","redirectUri":"app://cb","clientId":"client"}
		""".trimIndent()

		val config = json.decodeFromString<IdpConfig>(legacyJson)

		assertEquals("client", config.clientId)
		assertNull(config.dpop)
	}

	@Test
	fun idpConfigRoundTripsWithDpopEnabled() {
		val config = IdpConfig(
			discoveryUrl = "https://idp.example.com",
			redirectUri = "app://cb",
			clientId = "client",
			dpop = DpopConfig(mode = DpopMode.OPPORTUNISTIC, allowSoftwareKeyFallback = true),
		)

		val decoded = json.decodeFromString<IdpConfig>(json.encodeToString(config))

		assertEquals(config, decoded)
		assertEquals(listOf(DpopAlgorithm.ES256), decoded.dpop?.supportedAlgorithms)
		assertTrue(decoded.dpop?.allowSoftwareKeyFallback ?: false)
	}

	@Test
	fun defaultDpopConfigIsEnforcedAndRequiresHardwareBacking() {
		val config = DpopConfig()

		assertEquals(DpopMode.ENFORCED, config.mode)
		assertEquals(listOf(DpopAlgorithm.ES256), config.supportedAlgorithms)
		assertFalse(config.allowSoftwareKeyFallback)
	}

	/**
	 * A saved flow persisted by the previous release carries no `dpopKey`. This goes through the hand-written
	 * `AuthorizationRequestSerializer`, which is the one place where a field can be dropped silently.
	 */
	@Test
	fun savedAuthorizationFlowWrittenBeforeDpopStillDeserializes() {
		val flow = json.decodeFromString<SavedAuthorizationFlow>(LEGACY_SAVED_FLOW)

		assertEquals("client", flow.authorizationRequest.clientId)
		assertEquals("app://cb", flow.authorizationRequest.redirectUri)
		assertEquals("verifier", flow.codeChallenge.codeVerifier)
		assertEquals("https://idp.example.com", flow.providerMetadata.issuer)
		assertNull(flow.dpopKey)
	}

	@Test
	fun savedAuthorizationFlowRoundTripsWithADpopKey() {
		val original = json.decodeFromString<SavedAuthorizationFlow>(LEGACY_SAVED_FLOW).copy(
			dpopKey = DpopKeyRef("${DpopKeyRef.ALIAS_PREFIX}xyz", DpopAlgorithm.ES256, isHardwareBacked = false),
		)

		val decoded = json.decodeFromString<SavedAuthorizationFlow>(json.encodeToString(original))

		assertNotNull(decoded.dpopKey)
		assertEquals(original.dpopKey, decoded.dpopKey)
		assertEquals(original.authorizationRequest.state, decoded.authorizationRequest.state)
	}

	companion object {
		private val LEGACY_SAVED_FLOW = """
			{
			  "authorizationRequest": {
			    "response_type": "code",
			    "client_id": "client",
			    "redirect_uri": "app://cb",
			    "scope": "openid",
			    "state": "the-state"
			  },
			  "codeChallenge": {
			    "codeVerifier": "verifier",
			    "codeChallenge": "challenge",
			    "codeChallengeMethod": "S256"
			  },
			  "providerMetadata": {
			    "issuer": "https://idp.example.com",
			    "authorization_endpoint": "https://idp.example.com/auth",
			    "token_endpoint": "https://idp.example.com/token",
			    "jwks_uri": "https://idp.example.com/jwks",
			    "response_types_supported": ["code"],
			    "subject_types_supported": ["public"],
			    "id_token_signing_alg_values_supported": ["RS256"]
			  }
			}
		""".trimIndent()
	}
}
