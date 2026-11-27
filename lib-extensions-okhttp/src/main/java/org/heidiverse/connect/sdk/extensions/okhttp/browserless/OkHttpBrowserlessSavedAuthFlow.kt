package org.heidiverse.connect.sdk.extensions.okhttp.browserless

import kotlinx.serialization.Serializable
import org.heidiverse.connect.sdk.data.SavedAuthorizationFlow
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessSavedAuthFlow
import org.heidiverse.connect.sdk.extensions.okhttp.common.OkHttpCookieStoreData

@Serializable
data class OkHttpBrowserlessSavedAuthFlow(
	override val savedAuthorizationFlow: SavedAuthorizationFlow,
	override val cookieStoreData: OkHttpCookieStoreData
) : BrowserlessSavedAuthFlow
