package org.heidiverse.connect.sdk.okhttp.networking

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.heidiverse.connect.sdk.configuration.HttpClientConfiguration
import java.util.concurrent.TimeUnit

class OkHttpClientFactory(
	private val httpClientConfiguration: HttpClientConfiguration,
) {

	fun create(cookieJar: CookieJar? = null): OkHttpClient {
		return OkHttpClient.Builder()
			.callTimeout(httpClientConfiguration.requestTimeoutMillis, TimeUnit.MILLISECONDS)
			.connectTimeout(httpClientConfiguration.requestTimeoutMillis, TimeUnit.MILLISECONDS)
			.readTimeout(httpClientConfiguration.requestTimeoutMillis, TimeUnit.MILLISECONDS)
			.writeTimeout(httpClientConfiguration.requestTimeoutMillis, TimeUnit.MILLISECONDS)
			.addInterceptor { chain ->
				val requestWithUserAgent = chain.request()
					.newBuilder()
					.header("User-Agent", httpClientConfiguration.userAgent)
					.build()

				chain.proceed(requestWithUserAgent)
			}
			.apply { cookieJar?.let { this.cookieJar(it) } }
			.build()
	}

}