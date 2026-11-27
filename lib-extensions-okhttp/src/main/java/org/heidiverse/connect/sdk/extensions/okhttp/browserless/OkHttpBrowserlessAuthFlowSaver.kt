package org.heidiverse.connect.sdk.extensions.okhttp.browserless

import org.heidiverse.connect.sdk.data.SavedAuthorizationFlow
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessAuthFlowSaver
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessSavedAuthFlow
import org.heidiverse.connect.sdk.extensions.common.CommonCookieStore
import org.heidiverse.connect.sdk.extensions.okhttp.common.OkHttpCommonCookieStore

class OkHttpBrowserlessAuthFlowSaver(
	val cookieStore: CommonCookieStore,
) : BrowserlessAuthFlowSaver {
	override fun save(authFlow: SavedAuthorizationFlow): BrowserlessSavedAuthFlow =
		OkHttpBrowserlessSavedAuthFlow(
			savedAuthorizationFlow = authFlow,
			cookieStoreData = (cookieStore as OkHttpCommonCookieStore).save()
		)
}