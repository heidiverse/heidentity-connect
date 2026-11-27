package org.heidiverse.connect.sdk.extensions.browserless

import org.heidiverse.connect.sdk.data.SavedAuthorizationFlow
import org.heidiverse.connect.sdk.extensions.common.CookieStoreData

interface BrowserlessSavedAuthFlow {
	val savedAuthorizationFlow: SavedAuthorizationFlow

	val cookieStoreData: CookieStoreData
}
