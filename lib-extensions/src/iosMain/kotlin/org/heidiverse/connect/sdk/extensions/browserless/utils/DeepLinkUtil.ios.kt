package org.heidiverse.connect.sdk.extensions.browserless.utils

import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSURL
import platform.UIKit.UIApplication


class IOSDeepLinkUtil() : DeepLinkUtil {
    override fun openUri(uri: String) {
        val nsUrl = NSURL(string = uri)
        UIApplication.sharedApplication.openURL(
            url = nsUrl,
            options = mapOf<Any?, Any?>(),
            completionHandler = { success ->
                if (!success) {
                    println("Failed to open URI: $uri")
                }
            }
        )
    }

    override fun isAppInstalled(uri: String): Boolean {
        val nsUrl = NSURL(string = uri)
        return UIApplication.sharedApplication.canOpenURL(nsUrl)
    }
}
actual val deepLinkUtilModule: Module = module {
    single<DeepLinkUtil> { IOSDeepLinkUtil() }
}