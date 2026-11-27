package org.heidiverse.connect.sdk.extensions.common

interface CommonCookieStore {
    fun restore(data: CookieStoreData)

    // We only care about clearing the store currently
    fun clear()
}