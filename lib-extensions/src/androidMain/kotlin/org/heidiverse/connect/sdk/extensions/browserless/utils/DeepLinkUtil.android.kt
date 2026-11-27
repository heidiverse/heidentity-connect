package org.heidiverse.connect.sdk.extensions.browserless.utils

import org.koin.core.module.Module

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import org.koin.dsl.module

class AndroidDeepLinkUtil(private var context: Context) : DeepLinkUtil {
    private val browserPackageNames by lazy {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://definitely-wont-be-registered-by-another-app.example.com"))
        val browserInfoList = context.packageManager.queryIntentActivities(
            browserIntent, PackageManager.MATCH_ALL
        )
        browserInfoList.map { it.activityInfo.packageName }
    }

    override fun isAppInstalled(uri: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val packageManager = context.packageManager

        val resolveInfoList = packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_DEFAULT_ONLY
        )

        return resolveInfoList.any { it.activityInfo.packageName !in browserPackageNames }
    }

    override fun openUri(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

actual val deepLinkUtilModule: Module = module {
    single<DeepLinkUtil> { AndroidDeepLinkUtil(get()) }
}