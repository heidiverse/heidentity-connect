package org.heidiverse.connect.sdk.extensions.browserless.utils

import org.koin.core.module.Module

interface DeepLinkUtil {
    fun openUri(uri: String)

    fun isAppInstalled(uri: String): Boolean
}

expect val deepLinkUtilModule: Module