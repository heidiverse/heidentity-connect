package org.heidiverse.connect.sdk.browser

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity that receives the redirect URI after completing the authorization flow and forwards it to the [AuthorizationActivity].
 */
class RedirectUriReceiverActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		startActivity(AuthorizationActivity.createResponseHandlingIntent(this, intent.data))
		finish()
	}

}