package com.skt.nugu.sdk.platform.android.service.webkit

import junit.framework.TestCase
import org.junit.Assert

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch

class JavascriptObjectReceiverTest : TestCase() {
    @Test
    fun testOpenExternalApp() {
        val receiver = JavascriptObjectReceiver(object : JavascriptObjectReceiver.Listener {
            override fun openExternalApp(androidScheme: String?, androidAppId: String?) {
                Assert.assertEquals("Scheme", androidScheme)
                Assert.assertEquals("AppId", androidAppId)
            }
            override fun openInAppBrowser(url: String) {}
            override fun closeWindow(reason: String?) {}
            override fun setTitle(title: String) {}
            override fun fixedTextZoom() {}
            override fun requestActiveRoutine() {}
        })
        receiver.openExternalApp("{\n" +
                "    \"method\": \"openExternalApp\",\n" +
                "    \"body\": {\n" +
                "    \"androidScheme\": \"Scheme\",\n" +
                "    \"androidAppId\": \"AppId\"\n" +
                "    }\n" +
                "}")
    }

    @Test
    fun testOpenInAppBrowser() {
        val receiver = JavascriptObjectReceiver(object : JavascriptObjectReceiver.Listener {
            override fun openExternalApp(androidScheme: String?, androidAppId: String?) {}
            override fun openInAppBrowser(url: String) {
                Assert.assertEquals("https://nugu", url)
            }
            override fun closeWindow(reason: String?) {}
            override fun setTitle(title: String) {}
            override fun fixedTextZoom() {}
            override fun requestActiveRoutine() {}
        })
        receiver.openInAppBrowser("{\n" +
                "    \"method\": \"openInAppBrowser\",\n" +
                "    \"body\": {\n" +
                "    \"url\": \"https://nugu\"\n" +
                "    }\n" +
                "    }")
    }

    @Test
    fun testCloseWindow() {
        val receiver = JavascriptObjectReceiver(object : JavascriptObjectReceiver.Listener {
            override fun openExternalApp(androidScheme: String?, androidAppId: String?) {}
            override fun openInAppBrowser(url: String) {}
            override fun closeWindow(reason: String?) {
                Assert.assertEquals("WITHDRAWN_USER", reason)
            }
            override fun setTitle(title: String) {}
            override fun fixedTextZoom() {}
            override fun requestActiveRoutine() {}
        })
        receiver.closeWindow("{\n" +
                "    \"method\": \"closeWindow\" ,\n" +
                "    \"body\": {\n" +
                "    \"reason\": \"WITHDRAWN_USER\"\n" +
                "    }\n" +
                "    }")
    }

    @Test
    fun testSetTitle() {
        val receiver = JavascriptObjectReceiver(object : JavascriptObjectReceiver.Listener {
            override fun openExternalApp(androidScheme: String?, androidAppId: String?) {}
            override fun openInAppBrowser(url: String) {}
            override fun closeWindow(reason: String?) {}
            override fun setTitle(title: String) {
                Assert.assertEquals("settings", title)
            }
            override fun fixedTextZoom() {}
            override fun requestActiveRoutine() {}
        })
        receiver.setTitle("{\n" +
                "    \"method\": \"setTitle\" ,\n" +
                "    \"body\": {\n" +
                "    \"title\": \"settings\"\n" +
                "    }\n" +
                "    }")
    }

    @Test
    fun testFixedTextZoom() {
        val receiver = JavascriptObjectReceiver(object : JavascriptObjectReceiver.Listener {
            override fun openExternalApp(androidScheme: String?, androidAppId: String?) {}
            override fun openInAppBrowser(url: String) {}
            override fun closeWindow(reason: String?) {}
            override fun setTitle(title: String) {}
            override fun fixedTextZoom() {
                Assert.assertTrue("called", true)
            }
            override fun requestActiveRoutine() {}
        })
        receiver.fixedTextZoom("{\"method\": \"fixedTextZoom\"}")
    }

    @Test
    fun testRequestActiveRoutine() {
        val receiver = JavascriptObjectReceiver(object : JavascriptObjectReceiver.Listener {
            override fun openExternalApp(androidScheme: String?, androidAppId: String?) {}
            override fun openInAppBrowser(url: String) {}
            override fun closeWindow(reason: String?) {}
            override fun setTitle(title: String) {}
            override fun fixedTextZoom() {}
            override fun requestActiveRoutine() {
                Assert.assertTrue("called", true)
            }
        })
        receiver.requestActiveRoutine("{\"method\": \"requestActiveRoutine\"}")
    }
}