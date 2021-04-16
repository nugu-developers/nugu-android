/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.skt.nugu.sdk.platform.android.login.auth

import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import java.util.*

class NuguOAuthTest : TestCase() {
    @Test
    fun testSetOptions() {
        ConfigurationStore.configure("{\n" +
                "    \"OAuthServerUrl\": \"https://api.sktnugu.com\",\n" +
                "    \"OAuthClientId\": \"app.nugu.test\",\n" +
                "    \"OAuthClientSecret\": \"12121212-eee-ddd-ccc-12121212\",\n" +
                "    \"OAuthRedirectUri\": \"nugu.user.app.nugu.test://auth\",\n" +
                "    \"PoCId\": \"app.nugu.test\",\n" +
                "    \"DeviceTypeCode\": \"APP\"\n" +
                "}")
        NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        val options = NuguOAuth.getClient().getOptions()
        Assert.assertNotNull(options)
        Assert.assertEquals(options?.clientId, "app.nugu.test")
        Assert.assertEquals(options?.clientSecret, "12121212-eee-ddd-ccc-12121212")
        Assert.assertEquals(options?.redirectUri, "nugu.user.app.nugu.test://auth")
    }

    @Test
    fun testSetOptions_MultipleTimes() {
        ConfigurationStore.configure("{\n" +
                "    \"OAuthServerUrl\": \"https://api.sktnugu.com\",\n" +
                "    \"OAuthClientId\": \"app.nugu.test\",\n" +
                "    \"OAuthClientSecret\": \"12121212-eee-ddd-ccc-12121212\",\n" +
                "    \"OAuthRedirectUri\": \"nugu.user.app.nugu.test://auth\",\n" +
                "    \"PoCId\": \"app.nugu.test\",\n" +
                "    \"DeviceTypeCode\": \"APP\"\n" +
                "}")
        NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())

        Assert.assertEquals(NuguOAuth.getClient().getOptions()?.deviceUniqueId, "device1")

        NuguOAuth.getClient().setOptions(newOptions = NuguOAuthOptions.Builder()
            .deviceUniqueId("device2")
            .build())

        val options = NuguOAuth.getClient().getOptions()
        Assert.assertNotNull(options)
        Assert.assertEquals(options?.deviceUniqueId, "device2")
        Assert.assertEquals(options?.clientId, "app.nugu.test")
        Assert.assertEquals(options?.clientSecret, "12121212-eee-ddd-ccc-12121212")
        Assert.assertEquals(options?.redirectUri, "nugu.user.app.nugu.test://auth")
    }

    @Test
    fun testOAuthServerUrl_MultipleTimes() {
        ConfigurationStore.configure("{\n" +
                "    \"OAuthServerUrl\": \"https://1.sktnugu.com\",\n" +
                "    \"OAuthClientId\": \"app.nugu.test\",\n" +
                "    \"OAuthClientSecret\": \"12121212-eee-ddd-ccc-12121212\",\n" +
                "    \"OAuthRedirectUri\": \"nugu.user.app.nugu.test://auth\",\n" +
                "    \"PoCId\": \"app.nugu.test\",\n" +
                "    \"DeviceTypeCode\": \"TEST\"\n" +
                "}")
        NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        Assert.assertEquals(NuguOAuth.getClient().baseUrl(), "https://1.sktnugu.com")

        ConfigurationStore.configure("{\n" +
                "    \"OAuthServerUrl\": \"https://2.sktnugu.com\",\n" +
                "    \"OAuthClientId\": \"app.nugu.test\",\n" +
                "    \"OAuthClientSecret\": \"12121212-eee-ddd-ccc-12121212\",\n" +
                "    \"OAuthRedirectUri\": \"nugu.user.app.nugu.test://auth\",\n" +
                "    \"PoCId\": \"app.nugu.test\",\n" +
                "    \"DeviceTypeCode\": \"TEST\"\n" +
                "}")
        Assert.assertEquals(NuguOAuth.getClient().baseUrl(), "https://2.sktnugu.com")
    }

    @Test
    fun testSetCredentials() {
        NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        NuguOAuth.getClient().setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "",
                scope = "device:S.I.D."
            )
        )
        Assert.assertTrue(NuguOAuth.getClient().isTidLogin())
        Assert.assertFalse(NuguOAuth.getClient().isAnonymouslyLogin())
        Assert.assertTrue(NuguOAuth.getClient().isSidSupported())
        Assert.assertFalse(NuguOAuth.getClient().isExpired())
    }

    @Test
    fun testExpired() {
        NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        NuguOAuth.getClient().setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 1L,
                issuedTime = Date().time,
                tokenType = "",
                scope = "device:S.I.D."
            )
        )
        Assert.assertFalse(NuguOAuth.getClient().isExpired())
        NuguOAuth.getClient().setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = -1L,
                issuedTime = Date().time,
                tokenType = "",
                scope = "device:S.I.D."
            )
        )
        Assert.assertTrue(NuguOAuth.getClient().isExpired())
    }

    @Test
    fun testIsSidSupported() {
        NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        NuguOAuth.getClient().setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 1L,
                issuedTime = Date().time,
                tokenType = "",
                scope = "device:S.I.D."
            )
        )
        Assert.assertTrue(NuguOAuth.getClient().isSidSupported())
        NuguOAuth.getClient().setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 1L,
                issuedTime = Date().time,
                tokenType = "",
                scope = ""
            )
        )
        Assert.assertFalse(NuguOAuth.getClient().isSidSupported())
    }
}