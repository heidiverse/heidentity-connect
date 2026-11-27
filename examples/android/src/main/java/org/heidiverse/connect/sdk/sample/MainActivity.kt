package org.heidiverse.connect.sdk.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import org.heidiverse.connect.sdk.sample.feature.oidc.OidcFragment
import org.heidiverse.connect.sdk.sample.feature.oidc.OidcViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : AppCompatActivity() {

	private val viewModel by viewModel<OidcViewModel>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_main)

		if (savedInstanceState == null) {
			supportFragmentManager.commit {
				replace(R.id.fragmentContainerView, OidcFragment.newInstance())
			}

			// The App2App callback started the app, e.g. because the OS killed it while the user was in the
			// user-interacting app. Only on the first creation: after a configuration change, the intent was already
			// handled.
			handleApp2AppCallback(intent)
		}
	}

	/**
	 * The App2App callback, while the activity is still alive. It is a singleTask activity, see AndroidManifest.xml.
	 */
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		handleApp2AppCallback(intent)
	}

	private fun handleApp2AppCallback(intent: Intent) {
		if (intent.action != Intent.ACTION_VIEW) return
		// The manager ignores deep links that aren't its native callback URI
		intent.dataString?.let { viewModel.handleApp2AppCallback(it) }
	}

}
