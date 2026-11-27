package org.heidiverse.connect.sdk.extensions.okhttp

import org.heidiverse.connect.sdk.HeidentityConnect
import org.heidiverse.connect.sdk.configuration.HttpClientConfiguration
import org.heidiverse.connect.sdk.extensions.browserless.BrowserlessAuthFlowSaver
import org.heidiverse.connect.sdk.extensions.browserless.networking.BrowserlessAuthorizationService
import org.heidiverse.connect.sdk.extensions.common.CommonCookieStore
import org.heidiverse.connect.sdk.extensions.common.CookieStores
import org.heidiverse.connect.sdk.extensions.okhttp.browserless.OkHttpBrowserlessAuthFlowSaver
import org.heidiverse.connect.sdk.extensions.okhttp.browserless.OkHttpBrowserlessAuthorizationService
import org.heidiverse.connect.sdk.extensions.okhttp.common.OkHttpCommonCookieStore
import org.heidiverse.connect.sdk.okhttp.networking.OkHttpClientFactory
import org.koin.core.qualifier.named
import org.koin.dsl.module

object HeidentityConnectBrowserlessOkHttp {
	fun initialize(
		httpClientConfiguration: HttpClientConfiguration = HttpClientConfiguration(),
	) {
		val cookieStore = OkHttpCommonCookieStore()
		val httpClient = OkHttpClientFactory(httpClientConfiguration).create(cookieStore)

		HeidentityConnect.loadModules(
			listOf(
				module {
					single<BrowserlessAuthorizationService> {
						OkHttpBrowserlessAuthorizationService(httpClient, get())
					}
					single<CommonCookieStore>(named(CookieStores.Browserless)) { cookieStore }
					single<BrowserlessAuthFlowSaver> { OkHttpBrowserlessAuthFlowSaver(cookieStore) }
				}
			)
		)
	}
}
