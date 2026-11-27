package org.heidiverse.connect.sdk.extensions.ktor.browserless

import kotlinx.serialization.Serializable
import org.heidiverse.connect.sdk.data.SavedAuthorizationFlow
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessSavedAuthFlow
import org.heidiverse.connect.sdk.extensions.ktor.common.KtorCookieStoreData

@Serializable
data class KtorBrowserlessSavedAuthFlow(
	override val savedAuthorizationFlow: SavedAuthorizationFlow,
	override val cookieStoreData: KtorCookieStoreData
) : BrowserlessSavedAuthFlow
