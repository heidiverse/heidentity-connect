package org.heidiverse.connect.sdk.extensions.okhttp.common

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.heidiverse.connect.sdk.extensions.common.CommonCookieStore
import org.heidiverse.connect.sdk.extensions.common.CookieStoreData
import java.util.concurrent.CopyOnWriteArrayList

class OkHttpCommonCookieStore : CommonCookieStore, CookieJar {
    private val cookieStore = CopyOnWriteArrayList<Cookie>()

    override fun restore(data: CookieStoreData) {
        if (data !is OkHttpCookieStoreData)
            return

        cookieStore.addAll(data.cookies.map { it.asOkHttp() })
    }

    fun save(): OkHttpCookieStoreData =
        OkHttpCookieStoreData(cookieStore.map { it.asSerializable() })

    override fun clear() {
        cookieStore.clear()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (newCookie in cookies) {
            // Cookies are identified by the (Name, Domain, Path) triple https://datatracker.ietf.org/doc/html/rfc6265#section-5.3
            cookieStore.removeAll { existing ->
                existing.name == newCookie.name &&
                        existing.domain == newCookie.domain &&
                        existing.path == newCookie.path
            }
            cookieStore.add(newCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()

        cookieStore.removeAll { it.expiresAt < now }

        return cookieStore.filter { it.matches(url) }
    }
}
