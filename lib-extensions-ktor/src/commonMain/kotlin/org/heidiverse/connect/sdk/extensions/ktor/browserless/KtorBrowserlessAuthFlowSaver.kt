package org.heidiverse.connect.sdk.extensions.ktor.browserless

import org.heidiverse.connect.sdk.data.SavedAuthorizationFlow
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessAuthFlowSaver
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessSavedAuthFlow
import org.heidiverse.connect.sdk.extensions.common.CommonCookieStore
import org.heidiverse.connect.sdk.extensions.ktor.common.KtorCommonCookieStore

class KtorBrowserlessAuthFlowSaver(
	val cookieStore: CommonCookieStore,
) : BrowserlessAuthFlowSaver {
	override fun save(authFlow: SavedAuthorizationFlow): BrowserlessSavedAuthFlow =
		KtorBrowserlessSavedAuthFlow(
			savedAuthorizationFlow = authFlow,
			cookieStoreData = (cookieStore as KtorCommonCookieStore).save()
		)
}