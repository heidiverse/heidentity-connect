package org.heidiverse.connect.sdk.sample.feature.oidc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.heidiverse.connect.sdk.browser.AuthorizationRequestLauncher
import org.heidiverse.connect.sdk.networking.authorization.AuthorizationResponse
import org.heidiverse.connect.sdk.sample.shared.App2AppFlowStorage
import org.heidiverse.connect.sdk.sample.shared.IdpConfigState
import org.heidiverse.connect.sdk.sample.shared.IdpConfigStorage
import org.heidiverse.connect.sdk.sample.shared.OidcSampleManager
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Scoped to the activity rather than the fragment, so that MainActivity can pass App2App callbacks to it
 */
class OidcViewModel(
	idpConfigStorage: IdpConfigStorage,
	app2appFlowStorage: App2AppFlowStorage,
) : ViewModel() {

	companion object {
		val koinModule = module {
			viewModelOf(::OidcViewModel)
		}
	}

	private val manager = OidcSampleManager(idpConfigStorage, app2appFlowStorage, viewModelScope)

	val idpConfig = manager.idpConfig
	val loginState = manager.loginState

	fun setIdpConfig(idpConfig: IdpConfigState) = manager.setIdpConfig(idpConfig)

	fun startIdpDiscovery() = manager.startIdpDiscovery()

	fun startLogin(launcher: AuthorizationRequestLauncher, scope: String, prompt: String) {
		manager.startLogin(launcher, scope, prompt)
	}

	fun handleAuthorizationResponse(response: AuthorizationResponse) = manager.handleAuthorizationResponse(response)

	fun startApp2AppLogin(scope: String, prompt: String) = manager.startApp2AppLogin(scope, prompt)

	fun handleApp2AppCallback(url: String) = manager.handleApp2AppCallback(url)

	fun refreshTokens() = manager.refreshTokens()

	fun logout() = manager.logout()

}
