package org.heidiverse.connect.sdk.sample

import android.app.Application
import android.util.Log
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import org.heidiverse.connect.sdk.configuration.HttpClientConfiguration
import org.heidiverse.connect.sdk.extensions.ktor.HeidentityConnectBrowserlessKtor
import org.heidiverse.connect.sdk.ktor.HeidentityConnectKtor
import org.heidiverse.connect.sdk.ktor.networking.KtorHttpClientFactory
import org.heidiverse.connect.sdk.sample.di.viewModelsModule
import org.heidiverse.connect.sdk.sample.feature.storage.di.storageModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OidcApplication : Application() {

	override fun onCreate() {
		super.onCreate()

		HeidentityConnectKtor(this).initialize(httpClient = createLoggingHttpClient())
		// The browserless extension, for the App2App login. Must be initialized after the SDK itself.
		HeidentityConnectBrowserlessKtor.initialize()

		startKoin {
			androidContext(this@OidcApplication)
			modules(
				storageModule(),
				viewModelsModule(),
			)
		}
	}

	/**
	 * The SDK's own HTTP client, with full request and response logging added on top. This is what prints the token
	 * endpoint's response — the complete body of the authorization code exchange and of every token refresh — to
	 * logcat under the tag [HTTP_LOG_TAG].
	 *
	 * Only for a sample app: the log then contains the access, refresh and ID tokens in clear text, as well as the
	 * Authorization and DPoP headers of every request.
	 */
	private fun createLoggingHttpClient() = KtorHttpClientFactory(HttpClientConfiguration()).create()
		.config {
			install(Logging) {
				level = LogLevel.ALL
				logger = object : Logger {
					override fun log(message: String) {
						// Log.d truncates at ~4 kB per line, and an ID token alone can exceed that
						message.chunked(3000).forEach { Log.d(HTTP_LOG_TAG, it) }
					}
				}
			}
		}

	companion object {
		private const val HTTP_LOG_TAG = "HeidentityConnectHttp"
	}

}
