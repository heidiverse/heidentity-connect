package org.heidiverse.connect.sdk.extensions.sessiontransfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.heidiverse.connect.sdk.configuration.OidcConfiguration
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.di.HeidentityConnectKoinComponent
import org.heidiverse.connect.sdk.exception.HeidentityConnectException
import org.heidiverse.connect.sdk.exception.MetadataException
import org.heidiverse.connect.sdk.extensions.sessiontransfer.SessionTransferErrorType
import org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.SessionTransferProviderMetadata
import org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.SessionTransferService
import org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.token.SessionTransferTokenRequestBody
import org.heidiverse.connect.sdk.tokenhandling.DefaultTokenHandler
import org.koin.core.component.inject

class SessionTransferController(
	private val idpConfig: IdpConfig,
) : HeidentityConnectKoinComponent {

	private val configuration by inject<OidcConfiguration>()
	private val authService by inject<SessionTransferService>()
	private val json by inject<Json>()

	suspend fun getSessionTransferUrl(redirectUri: String): Result<String> = withContext(Dispatchers.IO) {
		val rawMetadata = configuration.authStorage.getProviderMetadataRaw()
			?: return@withContext Result.failure(
				MetadataException(
					errorType = SessionTransferErrorType.SESSION_TRANSFER,
					reason = MetadataException.Reason.NOT_AVAILABLE,
					message = "Provider metadata not available",
				)
			)
		val metadata = runCatching {
			json.decodeFromString<SessionTransferProviderMetadata>(rawMetadata)
		}.getOrElse { return@withContext Result.failure(mapFailure(it)) }
		val tokenEndpoint = metadata.sttCreateEndpoint
			?: return@withContext Result.failure(
				MetadataException(
					errorType = SessionTransferErrorType.SESSION_TRANSFER,
					reason = MetadataException.Reason.MISSING_FIELD,
					fieldName = "sttCreateEndpoint",
					message = "sttCreateEndpoint not defined",
				)
			)
		val sttUseEndpoint = metadata.sttUseEndpoint
			?: return@withContext Result.failure(
				MetadataException(
					errorType = SessionTransferErrorType.SESSION_TRANSFER,
					reason = MetadataException.Reason.MISSING_FIELD,
					fieldName = "sttUseEndpoint",
					message = "sttUseEndpoint not defined",
				)
			)

		return@withContext try {
			val token = authService.createSessionTransferToken(
				tokenEndpoint,
				DefaultTokenHandler(idpConfig),
				SessionTransferTokenRequestBody(redirectUri),
			)
			Result.success(sttUseEndpoint + "?token=" + token)
		} catch (e: Exception) {
			Result.failure(mapFailure(e))
		}
	}

	private fun mapFailure(e: Throwable): Exception {
		return when (e) {
			is HeidentityConnectException -> e
			is SerializationException -> MetadataException(
				errorType = SessionTransferErrorType.SESSION_TRANSFER,
				reason = MetadataException.Reason.DESERIALIZATION,
				message = e.message,
				cause = e,
			)
			else -> HeidentityConnectException(SessionTransferErrorType.SESSION_TRANSFER, e.message, e)
		}
	}
}
