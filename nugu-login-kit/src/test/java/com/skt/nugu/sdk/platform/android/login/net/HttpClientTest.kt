package com.skt.nugu.sdk.platform.android.login.net

import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthClient
import org.json.JSONException
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.MalformedURLException

class HttpClientTest {
    @Test
    fun testGetConnection() {
        val delegate = object : NuguOAuthClient.UrlDelegate {
            override fun baseUrl(): String {
                return "https://localhost"
            }
        }
        val client = HttpClient(delegate)
        val header = Headers()
            .add("name", "value")
        val connection = client.getConnection(
            uri = "${delegate.baseUrl()}/v1/auth/oauth/device_authorization",
            method = "GET",header)
        Assert.assertNotNull(connection)
    }
    @Test(expected = MalformedURLException::class)
    fun testGetConnectionUnknownProtocol() {
        val delegate = object : NuguOAuthClient.UrlDelegate {
            override fun baseUrl(): String {
                return "test://localhost"
            }
        }
        val client = HttpClient(delegate)
        val header = Headers()
            .add("name", "value")
        val connection = client.getConnection(
            uri = "${delegate.baseUrl()}/v1/auth/oauth/device_authorization",
            method = "GET",header)
        Assert.assertNotNull(connection)
    }
    @Test
    fun testReadStream() {
        val delegate = object : NuguOAuthClient.UrlDelegate {
            override fun baseUrl(): String {
                return "https://localhost"
            }
        }
        val client = HttpClient(delegate)
        val content = "Hello !!"
        val contentArray = content.toByteArray()
        val inputStream = ByteArrayInputStream(contentArray)
        val result = client.readStream(inputStream.buffered())
        Assert.assertEquals(result, content)
    }
}