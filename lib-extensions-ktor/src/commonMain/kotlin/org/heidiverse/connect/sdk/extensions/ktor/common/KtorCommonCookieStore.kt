package org.heidiverse.connect.sdk.extensions.ktor.common

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.fillDefaults
import io.ktor.client.plugins.cookies.matches
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.util.date.getTimeMillis
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.heidiverse.connect.sdk.extensions.common.CommonCookieStore
import org.heidiverse.connect.sdk.extensions.common.CookieStoreData
import kotlin.math.min

class KtorCommonCookieStore(
    private val clock: () -> Long = { getTimeMillis() }
) : CommonCookieStore, CookiesStorage {
    // StateFlow is thread-safe: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/
    private val container = MutableStateFlow<List<CookieWithTimestamp>>(emptyList())
    // Note: Since there is no mutex, there might be a race condition between updating the container and setting oldestCookie value
    // but since oldestCookie is only used for optimizations (when to run the cleanup function) this is non-critical.
    private val oldestCookieExpiry = atomic(Long.MAX_VALUE)

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val now = clock()
        if (now >= oldestCookieExpiry.value) cleanup(now)

        return container.value
            .filter { it.cookie.matches(requestUrl) }
            .map { it.cookie }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        if (cookie.name.isBlank()) return

        val createdAt = clock()
        val newCookieData = CookieWithTimestamp(cookie.fillDefaults(requestUrl), createdAt)

        container.update { currentList ->
            // Cookies are identified by the (Name, Domain, Path) triple https://datatracker.ietf.org/doc/html/rfc6265#section-5.3
            val filteredList = currentList.filterNot { existing ->
                existing.cookie.name == cookie.name &&
                        existing.cookie.domain == cookie.domain &&
                        existing.cookie.path == cookie.path
            }
            filteredList + newCookieData
        }

        cookie.expirationTimestamp(createdAt)?.let { newExpiration ->
            if (oldestCookieExpiry.value > newExpiration) {
                oldestCookieExpiry.value = newExpiration
            }
        }
    }

    override fun close() {
    }

    private fun cleanup(currentTimestamp: Long) {
        // Remove all cookies that are expired (expiry < currentTimestamp)
        container.update { currentList ->
            currentList.filterNot { cookieData ->
                val expires = cookieData.cookie.expirationTimestamp(cookieData.createdAt)
                expires != null && expires < currentTimestamp
            }
        }

        // Calculate the new oldestCookie value
        val newOldest = container.value.fold(Long.MAX_VALUE) { acc, cookieData ->
            cookieData.cookie.expirationTimestamp(cookieData.createdAt)
                ?.let { min(acc, it) } ?: acc
        }

        oldestCookieExpiry.value = newOldest
    }

    private fun Cookie.expirationTimestamp(createdAt: Long): Long? =
        maxAge?.let { createdAt + it * 1000L } ?: expires?.timestamp

    override fun restore(data: CookieStoreData) {
        if (data !is KtorCookieStoreData) return

        container.value = data.cookies
        cleanup(clock())
    }

    fun save(): KtorCookieStoreData {
        return KtorCookieStoreData(container.value)
    }

    override fun clear() {
        container.value = emptyList()
        oldestCookieExpiry.value = Long.MAX_VALUE
    }
}