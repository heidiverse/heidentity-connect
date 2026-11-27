package org.heidiverse.connect.sdk.tokenhandling

import org.heidiverse.connect.sdk.data.AuthTokens
import org.heidiverse.connect.sdk.data.IdpConfig
import org.heidiverse.connect.sdk.logging.OidcEvent
import org.heidiverse.connect.sdk.logging.OidcEventLogger
import org.heidiverse.connect.sdk.repository.AuthorizationRepository
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier

internal actual class TokenRefresher actual constructor(
	private val authorizationRepository: AuthorizationRepository,
	private val logger: OidcEventLogger,
) {
	private var currentTaskId: UIBackgroundTaskIdentifier? = null

	actual suspend fun refreshTokens(idpConfig: IdpConfig): Result<AuthTokens> {
		currentTaskId = UIApplication.sharedApplication.beginBackgroundTaskWithExpirationHandler {
			logger.logEvent(OidcEvent.IosExpirationHandlerCalled)
			endBackgroundTask()
		}

		val result = authorizationRepository.refreshTokens(idpConfig)

		endBackgroundTask()

		return result
	}

	private fun endBackgroundTask() {
		currentTaskId?.let { UIApplication.sharedApplication.endBackgroundTask(it) }
	}
}