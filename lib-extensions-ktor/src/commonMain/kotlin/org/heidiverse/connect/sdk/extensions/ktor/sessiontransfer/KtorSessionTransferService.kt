package org.heidiverse.connect.sdk.extensions.ktor.sessiontransfer

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.heidiverse.connect.sdk.exception.HeidentityConnectException
import org.heidiverse.connect.sdk.extensions.ktor.extensions.fromNetworkCall
import org.heidiverse.connect.sdk.extensions.sessiontransfer.SessionTransferErrorType
import org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.SessionTransferService
import org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.token.SessionTransferTokenRequestBody
import org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.token.SessionTransferTokenResponseBody
import org.heidiverse.connect.sdk.ktor.networking.plugin.OidcAuth
import org.heidiverse.connect.sdk.networking.RequestType
import org.heidiverse.connect.sdk.tokenhandling.TokenHandler

class KtorSessionTransferService(
	private val httpClient: HttpClient,
) : SessionTransferService {

	companion object {
		private val CONTENT_TYPE_APPLICATION_JSON = ContentType.parse("application/json")
	}

	override suspend fun createSessionTransferToken(
		sttUrl: String,
		tokenHandler: TokenHandler,
		request: SessionTransferTokenRequestBody,
	): String {
		val client = httpClient.config {
			install(ContentNegotiation) {
				json(
					Json {
						ignoreUnknownKeys = true
						encodeDefaults = true
					}
				)
			}
			install(OidcAuth) {
				defaultRequestType = RequestType.ALWAYS_AUTHORIZED
				authorizedBaseUrls = setOf(sttUrl)
				this.tokenHandler = tokenHandler
			}
		}
		return try {
			client.post(sttUrl) {
				contentType(CONTENT_TYPE_APPLICATION_JSON)
				setBody(request)
			}.body<SessionTransferTokenResponseBody>().token
		} catch (e: Exception) {
			throw HeidentityConnectException.fromNetworkCall(e, SessionTransferErrorType.SESSION_TRANSFER)
		}
	}
}
