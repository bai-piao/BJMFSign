package com.bjmf.sign.android.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class SimpleCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies.removeAll { old ->
            cookies.any { new ->
                old.name == new.name && old.domain == new.domain && old.path == new.path
            }
        }
        this.cookies.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val iterator = cookies.iterator()
        val valid = mutableListOf<Cookie>()
        while (iterator.hasNext()) {
            val cookie = iterator.next()
            if (cookie.expiresAt < System.currentTimeMillis()) {
                iterator.remove()
            } else if (cookie.matches(url)) {
                valid.add(cookie)
            }
        }
        return valid
    }

    fun all(): List<Cookie> = cookies.toList()
}
