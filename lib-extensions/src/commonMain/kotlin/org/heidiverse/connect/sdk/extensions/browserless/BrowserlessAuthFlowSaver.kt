package org.heidiverse.connect.sdk.extensions.browserless

import org.heidiverse.connect.sdk.data.SavedAuthorizationFlow

interface BrowserlessAuthFlowSaver {
	fun save(authFlow: SavedAuthorizationFlow): BrowserlessSavedAuthFlow
}