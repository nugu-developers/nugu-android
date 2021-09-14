package com.skt.nugu.sdk.platform.android.login.auth

import org.junit.Assert
import org.junit.Test

class NuguOAuthOptionsTest {
    @Test
    fun testNuguOAuthOptions() {
        val response1 = NuguOAuthOptions(
            grantType = "dummy_grantType",
            clientId = "dummy_clientId",
            clientSecret = "dummy_clientSecret",
            redirectUri = "dummy_redirectUri",
            deviceUniqueId = "dummy_deviceUniqueId"
        )
        Assert.assertNotNull(response1)
        NuguOAuthOptions.Builder()
        val response2 = NuguOAuthOptions.Builder()
            .grantType("dummy_grantType")
            .clientId("dummy_clientId")
            .clientSecret("dummy_clientSecret")
            .redirectUri("dummy_redirectUri")
            .deviceUniqueId("dummy_deviceUniqueId")
            .build()
        Assert.assertNotNull(response2)
        Assert.assertEquals(response1, response2)
    }

}