package com.skt.nugu.sdk.platform.android.service.webkit

import android.webkit.CookieManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.net.URL

class NuguWebViewTest {
    companion object {
        private val FAILURE_URLS = listOf(
            "https://foo.com",
            "https://google.com",
            "http://foo2.com"
        )
        private val SUCCESS_URLS = listOf(
            "https://abc.foo.com/sub/intro.htm",
            "https://abc.foo.com/m/1/sub/intro.html",
            "https://abc.foo.com:8080/main/test.htm",
            "https://abc.foo.com/main/index.html",
            "https://abc.foo.com",
            "http://abc.foo.com"
        )
        private val HEADERS = "a=android"
        private val URL by lazy {
            URL("https://abc.foo.com/main/index.html").host
        }
    }

    @Test
    fun shouldNotGetCookieForHostNotInUrl() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie(URL, HEADERS)
        FAILURE_URLS.forEach {
            assertNotEquals(HEADERS, cookieManager.getCookie(it))
        }
    }

    @Test
    fun shouldGetCookieForUrl() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie(URL, HEADERS)
        SUCCESS_URLS.forEach {
            assertEquals(HEADERS, cookieManager.getCookie(it))
        }
    }
}