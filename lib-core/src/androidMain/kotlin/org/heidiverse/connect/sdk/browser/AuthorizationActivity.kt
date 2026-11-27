package org.heidiverse.connect.sdk.browser

import android.app.PendingIntent
import android.app.PendingIntent.CanceledException
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.serialization.json.Json
import org.heidiverse.connect.sdk.di.HeidentityConnectKoinComponent
import org.heidiverse.connect.sdk.extensions.optionalParcelable
import org.heidiverse.connect.sdk.extensions.requireParcelable
import org.heidiverse.connect.sdk.networking.authorization.AuthorizationResponse

internal class AuthorizationActivity : AppCompatActivity(), HeidentityConnectKoinComponent {

	companion object {
		private const val KEY_HAS_AUTHORIZATION_STARTED = "KEY_HAS_AUTHORIZATION_STARTED"
		private const val KEY_AUTH_INTENT = "KEY_AUTH_INTENT"
		private const val KEY_COMPLETE_INTENT = "KEY_COMPLETE_INTENT"
		private const val KEY_CANCEL_INTENT = "KEY_CANCEL_INTENT"

		/**
		 * Creates an intent to start the [AuthorizationActivity] with the provided [authIntent].
		 * The authorization result will be set as the activity result
		 */
		fun createStartForResultIntent(
			context: Context,
			authIntent: Intent,
		) = createStartIntent(context, authIntent, null, null)

		/**
		 * Creates an intent to start the [AuthorizationActivity] with the provided [authIntent].
		 * The authorization result will be sent to the provided [onCompleteCallback] and [onCancellationCallback] PendingIntents
		 */
		fun createStartIntent(
			context: Context,
			authIntent: Intent,
			onCompleteCallback: PendingIntent?,
			onCancellationCallback: PendingIntent?,
		) = Intent(context, AuthorizationActivity::class.java).apply {
			putExtra(KEY_AUTH_INTENT, authIntent)
			putExtra(KEY_COMPLETE_INTENT, onCompleteCallback)
			putExtra(KEY_CANCEL_INTENT, onCancellationCallback)
		}

		fun createResponseHandlingIntent(
			context: Context,
			responseUri: Uri?,
		) = Intent(context, AuthorizationActivity::class.java).apply {
			setData(responseUri)
			addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
		}

	}

	private var hasAuthorizationStarted = false

	private lateinit var authIntent: Intent
	private var completeIntent: PendingIntent? = null
	private var cancelIntent: PendingIntent? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val state = savedInstanceState
			?: intent.extras
			?: run {
				// Neither saved state nor intent extras, cannot proceed as we won't have an auth intent
				finish()
				return
			}

		extractState(state)
	}

	override fun onResume() {
		super.onResume()

		if (hasAuthorizationStarted) {
			// When the activity is resumed and authorization has already been started, check if there is a response URI in the
			// intent data and either call the completion or cancellation callback
			val responseUri = intent.data
			if (responseUri != null) {
				onAuthorizationCompleted(responseUri)
			} else {
				onAuthorizationCancelled()
			}
			finish()
		} else {
			// When the activity is resumed and authorization has not been started yet, start the authorization flow
			try {
				startActivity(authIntent)
				hasAuthorizationStarted = true
			} catch (e: ActivityNotFoundException) {
				onBrowserNotFound()
				finish()
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putBoolean(KEY_HAS_AUTHORIZATION_STARTED, hasAuthorizationStarted)
		outState.putParcelable(KEY_AUTH_INTENT, authIntent)
		outState.putParcelable(KEY_COMPLETE_INTENT, completeIntent)
		outState.putParcelable(KEY_CANCEL_INTENT, cancelIntent)
	}

	private fun extractState(state: Bundle) {
		hasAuthorizationStarted = state.getBoolean(KEY_HAS_AUTHORIZATION_STARTED, false)
		authIntent = state.requireParcelable<Intent>(KEY_AUTH_INTENT)
		completeIntent = state.optionalParcelable(KEY_COMPLETE_INTENT)
		cancelIntent = state.optionalParcelable(KEY_CANCEL_INTENT)
	}

	private fun onAuthorizationCompleted(responseUri: Uri) {
		val response = AuthorizationResponse.from(responseUri.toString())

		val responseData = response.toIntent { setData(responseUri) }
		if (response is AuthorizationResponse.Success) {
			sendResult(completeIntent, responseData, RESULT_OK)
		} else {
			sendResult(completeIntent, responseData, RESULT_CANCELED)
		}
	}

	private fun onAuthorizationCancelled() {
		val response = AuthorizationResponse.Cancellation
		sendResult(cancelIntent, response.toIntent(), RESULT_CANCELED)
	}

	private fun onBrowserNotFound() {
		val response = AuthorizationResponse.BrowserNotFoundError
		sendResult(cancelIntent, response.toIntent(), RESULT_CANCELED)
	}

	private fun sendResult(callback: PendingIntent?, data: Intent, resultCode: Int) {
		if (callback != null) {
			try {
				callback.send(this, 0, data)
			} catch (_: CanceledException) {
				setResult(resultCode, data)
			}
		} else {
			setResult(resultCode, data)
		}
	}

	private fun AuthorizationResponse.toIntent(block: Intent.() -> Unit = {}) = Intent().apply {
		putExtra(AuthorizationIntentManager.KEY_AUTHORIZATION_RESPONSE, Json.encodeToString(this@toIntent))
		this.block()
	}

}