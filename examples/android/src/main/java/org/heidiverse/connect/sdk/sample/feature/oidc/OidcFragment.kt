package org.heidiverse.connect.sdk.sample.feature.oidc

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.heidiverse.connect.sdk.extensions.registerAuthorizationRequestLauncher
import org.heidiverse.connect.sdk.sample.compose.theme.OidcTheme
import org.heidiverse.connect.sdk.sample.databinding.FragmentComposeBinding
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.core.component.KoinComponent

class OidcFragment : Fragment(), KoinComponent {

	companion object {
		val TAG = "OidcFragment"

		fun newInstance() = OidcFragment()
	}

	// Shared with MainActivity, which receives the App2App callback
	private val viewModel by activityViewModel<OidcViewModel>()

	private var _binding: FragmentComposeBinding? = null
	private val binding get() = _binding!!

	private val authRequestLauncher = registerAuthorizationRequestLauncher(
		onResponse = { response ->
			viewModel.handleAuthorizationResponse(response)
		},
	)

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = FragmentComposeBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.composeView.setContent {
			OidcTheme {
				OidcScreen(
					idpConfigState = viewModel.idpConfig.collectAsStateWithLifecycle(),
					loginState = viewModel.loginState.collectAsStateWithLifecycle(),
					onIdpConfigChanged = { idpConfig ->
						viewModel.setIdpConfig(idpConfig)
					},
					onLoginClicked = { scope, prompt ->
						viewModel.startLogin(authRequestLauncher, scope, prompt)
					},
					onApp2AppLoginClicked = { scope, prompt ->
						viewModel.startApp2AppLogin(scope, prompt)
					},
					onRefreshClicked = {
						viewModel.refreshTokens()
					},
					onLogoutClicked = {
						viewModel.logout()
					},
					onRetryClicked = {
						viewModel.startIdpDiscovery()
					},
				)
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

}