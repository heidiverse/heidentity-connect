package org.heidiverse.connect.sdk.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.heidiverse.connect.sdk.configuration.OidcConfiguration
import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.dpop.DpopConfig
import org.heidiverse.connect.sdk.dpop.DpopException
import org.heidiverse.connect.sdk.dpop.DpopKeyManager
import org.heidiverse.connect.sdk.dpop.DpopKeyRef
import org.heidiverse.connect.sdk.dpop.DpopMode
import org.heidiverse.connect.sdk.exception.*
import org.heidiverse.connect.sdk.logging.OidcEvent
import org.heidiverse.connect.sdk.networking.AuthorizationService
import org.heidiverse.connect.sdk.networking.configuration.ProviderMetadata
import org.heidiverse.connect.sdk.storage.AuthStorage
import org.heidiverse.connect.sdk.util.RandomGenerator
import org.heidiverse.connect.sdk.util.SingleEventFlow
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal class AuthorizationRepository(
	private val authService: AuthorizationService,
	private val configuration: OidcConfiguration,
	private val json: Json,
	private val dpopKeyManager: DpopKeyManager,
) {

	companion object {
		val koinModule = module {
			singleOf(::AuthorizationRepository)
		}
	}

	val authorizedState = configuration.authStorage.getAuthTokenFlow().map { it != null }.distinctUntilChanged()

	private val sessionExpiredMutable = SingleEventFlow<Boolean>()
	val sessionExpired = sessionExpiredMutable.asFlow()

	/**
	 * Serializes token refreshes across every token handler and controller. The tokens are shared, so with refresh token
	 * rotation, two refreshes running in parallel would make the second one fail with an invalid_grant error.
	 *
	 * Must be held by the callers of [refreshTokens] rather than taken inside it: the token handlers hold it around their
	 * check-then-refresh logic, and a [Mutex] is not reentrant.
	 */
	val tokenRefreshLock = Mutex()

	/** Special temporary auth tokens used during the token verification process */
	private var temporaryAuthTokens: AuthTokens? = null

	/** The alias of the last missing DPoP key reported as a session expiry, see [onDpopKeyMissing] */
	private var reportedMissingDpopKeyAlias: String? = null

	/**
	 * @return The currently stored [AuthTokens] or null
	 */
	fun getAuthTokens(): AuthTokens? {
		return configuration.authStorage.getCurrentAuthTokens()
	}

	fun getAuthTokenFlow(): Flow<AuthTokens?> {
		return configuration.authStorage.getAuthTokenFlow()
	}

	/**
	 * @return True if the [AuthStorage] contains any auth tokens
	 */
	fun isAuthorized(): Boolean {
		return configuration.authStorage.hasAuthTokens()
	}

	/**
	 * @return The currently stored [ProviderMetadata] or null
	 */
	fun getProviderMetadata(): ProviderMetadata? {
		return configuration.authStorage.getProviderMetadata()
	}

	/**
	 * Loads the provider metadata from the [OIDC discovery URL](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfig)
	 *
	 * @return The loaded [ProviderMetadata] or any resulting error
	 */
	suspend fun loadProviderMetadata(
		discoveryUrl: String,
		customHeaders: Map<String, String> = emptyMap()
	): Result<ProviderMetadata> = withContext(Dispatchers.IO) {
		try {
			val rawMetadata = authService.doDiscoveryRequest(discoveryUrl, customHeaders)
			val metadata = json.decodeFromString<ProviderMetadata>(rawMetadata)
			configuration.authStorage.setProviderMetadataRaw(rawMetadata)
			return@withContext Result.success(metadata)
		} catch (e: kotlinx.serialization.SerializationException) {
			val mappedException = MetadataException(
				errorType = AuthorizationErrorType.DISCOVERY,
				reason = MetadataException.Reason.DESERIALIZATION,
				message = e.message,
				cause = e,
			)
			configuration.logger.logEvent(OidcEvent.FetchingProviderMetadataFailed(mappedException))
			return@withContext Result.failure(mappedException)
		} catch (e: Exception) {
			val mappedException = authService.mapTokenRequestException(AuthorizationErrorType.DISCOVERY, e)
			configuration.logger.logEvent(OidcEvent.FetchingProviderMetadataFailed(mappedException))
			return@withContext Result.failure(mappedException)
		}
	}

	/**
	 * Exchanges the authorization [code] for the auth tokens via the [OIDC token endpoint](https://openid.net/specs/openid-connect-core-1_0.html#TokenEndpoint)
	 *
	 * @return The exchanged [AuthTokens] or any resulting error
	 */
	suspend fun exchangeAuthorizationCode(
		code: String,
		idpConfig: IdpConfig,
		codeVerifier: String?,
		customHeaders: Map<String, String> = emptyMap(),
		dpopKeyRef: DpopKeyRef? = null,
	): Result<AuthTokens> = withContext(Dispatchers.IO) {
		val exchangeCorrelationId = RandomGenerator.generateRandomString(bytes = 12)

		val tokenEndpoint = configuration.authStorage.getProviderMetadata()?.tokenEndpoint
			?: run {
				val ex = MetadataException(
					errorType = AuthorizationErrorType.AUTHORIZATION_CODE_EXCHANGE,
					reason = MetadataException.Reason.MISSING_FIELD,
					fieldName = "tokenEndpoint",
					message = "tokenEndpoint not defined",
				)
				configuration.logger.logEvent(OidcEvent.AuthorizationCodeExchangeFailed(ex, exchangeCorrelationId))
				return@withContext Result.failure(ex)
			}

		try {
			configuration.logger.logEvent(OidcEvent.AuthorizationCodeExchangeStarted(exchangeCorrelationId))
			val authTokens = authService.doTokenExchangeRequest(
				tokenEndpoint = tokenEndpoint,
				idpConfig = idpConfig,
				authorizationCode = code,
				codeVerifier = codeVerifier,
				customHeaders = customHeaders,
				dpopKeyRef = dpopKeyRef,
			).let {
				// Carry the key alongside the tokens, the refresh request needs it
				it.copy(dpopKey = dpopKeyRef)
			}
			checkDpopTokenType(idpConfig.dpop, authTokens)
			configuration.logger.logEvent(
				OidcEvent.AuthorizationCodeExchangeSuccessful(authTokens.expiresAt ?: 0, exchangeCorrelationId)
			)
			return@withContext Result.success(authTokens)
		} catch (e: Exception) {
			val mappedException = e as? DpopException
				?: authService.mapTokenRequestException(AuthorizationErrorType.AUTHORIZATION_CODE_EXCHANGE, e)
			if (mappedException is SessionExpiredException) {
				sessionExpiredMutable.emit(true)
			}
			configuration.logger.logEvent(OidcEvent.AuthorizationCodeExchangeFailed(mappedException, exchangeCorrelationId))
			return@withContext Result.failure(mappedException)
		}
	}

	/**
	 * Refreshes the auth tokens using the refresh token via the [OIDC token endpoint](https://openid.net/specs/openid-connect-core-1_0.html#RefreshTokens)
	 *
	 * @return The refreshed [AuthTokens] or any resulting error
	 */
	suspend fun refreshTokens(
		idpConfig: IdpConfig,
		customHeaders: Map<String, String> = emptyMap(),
	): Result<AuthTokens> = withContext(Dispatchers.IO) {
		val refreshCorrelationId = RandomGenerator.generateRandomString(bytes = 12)

		val previousTokens = configuration.authStorage.getCurrentAuthTokens()
		val refreshToken = previousTokens?.refreshToken
			?: run {
				val ex = MissingRefreshTokenException()
				configuration.logger.logEvent(OidcEvent.TokenRefreshFailed(ex, refreshCorrelationId))
				return@withContext Result.failure(ex)
			}

		val dpopKeyRef = previousTokens.dpopKey

		// Create a single combined correlation id that includes a short hash of the refresh token for tracing.
		val refreshCorrelationIdWithHash = "$refreshCorrelationId-${tokenHashLast6(refreshToken)}"
		configuration.logger.logEvent(OidcEvent.TokenRefreshStarted(refreshCorrelationIdWithHash))

		val tokenEndpoint = configuration.authStorage.getProviderMetadata()?.tokenEndpoint
			?: run {
				val ex = MetadataException(
					errorType = AuthorizationErrorType.TOKEN_REFRESH,
					reason = MetadataException.Reason.MISSING_FIELD,
					fieldName = "tokenEndpoint",
					message = "tokenEndpoint not defined",
				)
				configuration.logger.logEvent(OidcEvent.TokenRefreshFailed(ex, refreshCorrelationIdWithHash))
				return@withContext Result.failure(ex)
			}

		try {
			val newTokens = authService.doTokenRefreshRequest(
				tokenEndpoint = tokenEndpoint,
				idpConfig = idpConfig,
				refreshToken = refreshToken,
				customHeaders = customHeaders,
				dpopKeyRef = dpopKeyRef,
			).let {
				// The key survives the refresh
				it.copy(dpopKey = dpopKeyRef)
			}
			// token_type is re-checked on every refresh, because a provider's DPoP setting can be flipped server-side
			// mid-session. If the new tokens are rejected, the previous ones are kept.
			checkDpopTokenType(idpConfig.dpop, newTokens)

			// Per RFC 6749 section 6, the server MAY issue a new refresh token; if it doesn't, the client
			// must keep using the existing refresh token. Likewise, the refresh response might not contain
			// an ID token (OIDC Core section 12.2), in which case the existing one is kept.
			val mergedTokens = newTokens.copy(
				refreshToken = newTokens.refreshToken ?: refreshToken,
				idToken = newTokens.idToken ?: previousTokens.idToken,
			)

			val currentTokens = configuration.authStorage.getCurrentAuthTokens()
			if (currentTokens != null) {
				// Only update tokens if they have not been cleared in the meantime
				updateTokens(mergedTokens)
				configuration.logger.logEvent(
					OidcEvent.TokenRefreshSuccessful(
						expiresAt = mergedTokens.expiresAt ?: 0,
						correlationId = mergedTokens.refreshToken
							?.let { "$refreshCorrelationId-${tokenHashLast6(it)}" }
							?: refreshCorrelationIdWithHash,
					)
				)
				return@withContext Result.success(mergedTokens)
			} else {
				configuration.logger.logEvent(OidcEvent.TokensClearedDuringRefresh(refreshCorrelationIdWithHash))
				return@withContext Result.failure(UnauthorizedException())
			}

		} catch (e: Exception) {
			if (e is DpopException) {
				// A missing key has already expired the session through DpopProofProvider's onKeyMissing callback
				configuration.logger.logEvent(OidcEvent.TokenRefreshFailed(e, refreshCorrelationIdWithHash))
				return@withContext Result.failure(e)
			}

			val mappedException = authService.mapTokenRequestException(AuthorizationErrorType.TOKEN_REFRESH, e)
			if (mappedException is SessionExpiredException) {
				sessionExpiredMutable.emit(true)
			}
			configuration.logger.logEvent(OidcEvent.TokenRefreshFailed(mappedException, refreshCorrelationIdWithHash))
			return@withContext Result.failure(mappedException)
		}
	}

	/**
	 * Persists [tokens] and deletes the DPoP key of the tokens being replaced, unless the new tokens keep using it.
	 */
	fun updateTokens(tokens: AuthTokens?) {
		val previousKey = configuration.authStorage.getCurrentAuthTokens()?.dpopKey

		configuration.authStorage.setAuthTokens(tokens)

		if (previousKey != null && previousKey.alias != tokens?.dpopKey?.alias) {
			dpopKeyManager.deleteKey(previousKey)
		}
	}

	/**
	 * Called when a DPoP proof cannot be created because its key is gone (app data restored onto another device,
	 * keystore invalidated). If the stored tokens are bound to that key, both the access and the refresh token are
	 * unusable, so the session is expired right away instead of every request failing until the next token refresh.
	 *
	 * Reported only once per key, since every request made until the app reacts ends up here.
	 */
	internal fun onDpopKeyMissing(keyRef: DpopKeyRef) {
		val storedKey = configuration.authStorage.getCurrentAuthTokens()?.dpopKey ?: return
		if (storedKey.alias != keyRef.alias || storedKey.alias == reportedMissingDpopKeyAlias) return

		reportedMissingDpopKeyAlias = storedKey.alias
		configuration.logger.logEvent(OidcEvent.DpopKeyMissing(storedKey.alias))
		sessionExpiredMutable.emit(true)
	}

	/**
	 * Checks the `token_type` of tokens obtained with a DPoP proof against [DpopConfig.mode]. Sessions whose tokens
	 * carry no key (logged in before DPoP was enabled, or without a key in opportunistic mode) never sent a proof, so
	 * they are not affected.
	 *
	 * - [DpopMode.ENFORCED]: fails closed, since accepting the tokens would silently downgrade the session to plain
	 *   bearer tokens
	 * - [DpopMode.OPPORTUNISTIC]: accepts the tokens, which are then used as bearer tokens
	 *
	 * @throws DpopException with [DpopException.Reason.TOKEN_NOT_DPOP_BOUND]
	 */
	private fun checkDpopTokenType(dpopConfig: DpopConfig?, tokens: AuthTokens) {
		if (dpopConfig == null || tokens.dpopKey == null || tokens.isDpopBound()) return

		val message = "The authorization server issued token_type '${tokens.tokenType}' instead of DPoP"
		when (dpopConfig.mode) {
			DpopMode.OPPORTUNISTIC -> configuration.logger.logEvent(OidcEvent.DpopNotUsed(message))
			DpopMode.ENFORCED -> throw DpopException(
				errorType = AuthorizationErrorType.AUTHORIZATION,
				reason = DpopException.Reason.TOKEN_NOT_DPOP_BOUND,
				message = message,
			)
		}
	}

	private fun tokenHashLast6(token: String): String {
		// Use String.hashCode() to remain commonMain-compatible. This is deterministic but not cryptographically secure.
		val hash = token.hashCode().toLong() and 0xffffffffL
		val hex = hash.toString(16)
		return hex.padStart(6, '0').takeLast(6)
	}

	internal fun getAuthTokensWithTemporaryFallback(allowTemporaryAuthTokensDuringVerification: Boolean): AuthTokens? {
		return if (allowTemporaryAuthTokensDuringVerification) {
			val storedTokens = if (configuration.authStorage.hasAuthTokens()) {
				configuration.authStorage.getCurrentAuthTokens()
			} else {
				null
			}
			storedTokens ?: temporaryAuthTokens
		} else {
			configuration.authStorage.getCurrentAuthTokens()
		}
	}

	fun clearProviderMetadata() {
		configuration.authStorage.setProviderMetadataRaw(null)
	}

	/**
	 * Deletes every DPoP key in the secure storage except the one the stored tokens use and the ones in
	 * [additionalKeysToRetain] (the keys of flows that are currently in progress).
	 */
	internal fun pruneOrphanedDpopKeys(additionalKeysToRetain: Collection<DpopKeyRef?>) {
		val retained = additionalKeysToRetain + listOf(
			configuration.authStorage.getCurrentAuthTokens()?.dpopKey,
			temporaryAuthTokens?.dpopKey,
		)
		dpopKeyManager.pruneOrphanedKeys(retained)
	}

	fun clearAuthTokens() {
		temporaryAuthTokens = null

		updateTokens(null)
	}

	internal fun isTemporaryAuthorized(): Boolean {
		return temporaryAuthTokens != null
	}

	internal fun setTemporaryAuthTokens(tokens: AuthTokens?) {
		temporaryAuthTokens = tokens
	}

}
