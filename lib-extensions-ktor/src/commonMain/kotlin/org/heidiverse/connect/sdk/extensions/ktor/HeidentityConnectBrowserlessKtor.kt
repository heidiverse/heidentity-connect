package org.heidiverse.connect.sdk.extensions.ktor

import org.heidiverse.connect.sdk.HeidentityConnect
import org.heidiverse.connect.sdk.configuration.HttpClientConfiguration
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessAuthFlowSaver
import org.heidiverse.connect.sdk.extensions.browserless.networking.BrowserlessAuthorizationService
import org.heidiverse.connect.sdk.extensions.browserless.utils.deepLinkUtilModule
import org.heidiverse.connect.sdk.extensions.common.CommonCookieStore
import org.heidiverse.connect.sdk.extensions.common.CookieStores
import org.heidiverse.connect.sdk.extensions.ktor.browserless.KtorBrowserlessAuthFlowSaver
import org.heidiverse.connect.sdk.extensions.ktor.browserless.KtorBrowserlessAuthorizationService
import org.heidiverse.connect.sdk.extensions.ktor.common.KtorCommonCookieStore
import org.heidiverse.connect.sdk.ktor.networking.KtorHttpClientFactory
import org.koin.core.qualifier.named
import org.koin.dsl.module

object HeidentityConnectBrowserlessKtor {
    fun initialize(
        httpClientConfiguration: HttpClientConfiguration = HttpClientConfiguration(),
    ) {
        val cookieStore = KtorCommonCookieStore()
        val httpClient = KtorHttpClientFactory(httpClientConfiguration).create(cookieStore)
        HeidentityConnect.loadModules(
            listOf(
                module {
                    single<CommonCookieStore>(named(CookieStores.Browserless)) { cookieStore }
                    single<BrowserlessAuthFlowSaver> { KtorBrowserlessAuthFlowSaver(cookieStore) }
                    single<BrowserlessAuthorizationService>{ KtorBrowserlessAuthorizationService(httpClient) }
                },
                deepLinkUtilModule
            )
        )
    }
}