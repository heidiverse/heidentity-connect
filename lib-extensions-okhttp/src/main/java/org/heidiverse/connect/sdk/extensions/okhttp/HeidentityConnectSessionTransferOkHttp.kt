package org.heidiverse.connect.sdk.extensions.okhttp

import okhttp3.OkHttpClient
import org.heidiverse.connect.sdk.HeidentityConnect
import org.heidiverse.connect.sdk.extensions.okhttp.sessiontransfer.OkHttpSessionTransferService
import org.heidiverse.connect.sdk.extensions.sessiontransfer.networking.SessionTransferService
import org.koin.dsl.module

object HeidentityConnectSessionTransferOkHttp {
	fun initialize(httpClient: OkHttpClient = OkHttpClient()) {
		HeidentityConnect.loadModules(
			listOf(
				module {
					single<SessionTransferService> {
						OkHttpSessionTransferService(httpClient, get())
					}
				}
			)
		)
	}
}
