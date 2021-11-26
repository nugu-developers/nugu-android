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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.platform.android.login.exception.ClientUnspecifiedException
import com.skt.nugu.sdk.platform.android.login.view.NuguOAuthCallbackActivity
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
class NuguOAuthTest  {
    @Test
    fun testGetClient() {
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
        val client = NuguOAuth.getClient()
        Assert.assertNotNull(client)
    }


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
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        val options = client.getOptions()
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
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        Assert.assertEquals(client.getOptions()?.deviceUniqueId, "device1")

        client.setOptions(options = NuguOAuthOptions.Builder()
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
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        Assert.assertEquals(client.baseUrl(), "https://1.sktnugu.com")

        ConfigurationStore.configure("{\n" +
                "    \"OAuthServerUrl\": \"https://2.sktnugu.com\",\n" +
                "    \"OAuthClientId\": \"app.nugu.test\",\n" +
                "    \"OAuthClientSecret\": \"12121212-eee-ddd-ccc-12121212\",\n" +
                "    \"OAuthRedirectUri\": \"nugu.user.app.nugu.test://auth\",\n" +
                "    \"PoCId\": \"app.nugu.test\",\n" +
                "    \"DeviceTypeCode\": \"TEST\"\n" +
                "}")
        Assert.assertEquals(client.baseUrl(), "https://2.sktnugu.com")
    }

    @Test
    fun testSetCredentials() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "",
                scope = "device:S.I.D."
            )
        )
        Assert.assertTrue(client.isTidLogin())
        Assert.assertFalse(client.isAnonymouslyLogin())
        Assert.assertTrue(client.isSidSupported())
        Assert.assertFalse(client.isExpired())
    }

    @Test
    fun testExpired() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 1L,
                issuedTime = Date().time,
                tokenType = "",
                scope = "device:S.I.D."
            )
        )
        Assert.assertFalse(client.isExpired())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = -1L,
                issuedTime = Date().time,
                tokenType = "",
                scope = "device:S.I.D."
            )
        )
        Assert.assertTrue(client.isExpired())
    }

    @Test
    fun testIsSidSupported() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 1L,
                issuedTime = Date().time,
                tokenType = "",
                scope = "device:S.I.D."
            )
        )
        Assert.assertTrue(client.isSidSupported())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 1L,
                issuedTime = Date().time,
                tokenType = "",
                scope = ""
            )
        )
        Assert.assertFalse(client.isSidSupported())
    }

    @Test
    fun testAuthStateListener() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setAuthState(AuthStateListener.State.REFRESHED)
        Assert.assertEquals(client.getAuthState(), AuthStateListener.State.REFRESHED)
        client.setAuthState(AuthStateListener.State.EXPIRED)
        Assert.assertEquals(client.getAuthState(), AuthStateListener.State.EXPIRED)
        client.setAuthState(AuthStateListener.State.UNRECOVERABLE_ERROR)
        Assert.assertEquals(client.getAuthState(), AuthStateListener.State.UNRECOVERABLE_ERROR)
        client.setAuthState(AuthStateListener.State.UNINITIALIZED)
        Assert.assertEquals(client.getAuthState(), AuthStateListener.State.UNINITIALIZED)
    }

    @Test
    fun testOnAuthStateChanged() {
        val latch = CountDownLatch(1)
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        val listener = object : AuthStateListener {
            override fun onAuthStateChanged(newState: AuthStateListener.State): Boolean {
                Assert.assertEquals(client.getAuthState(), newState)
                latch.countDown()
                return true
            }
        }
        client.addAuthStateListener(listener)
        client.setAuthState(AuthStateListener.State.REFRESHED)
        latch.await()
        client.removeAuthStateListener(listener)
    }

    @Test
    fun testOnceLoginListener() {
        val listener = NuguOAuth.OnceLoginListener(object : NuguOAuthInterface.OnLoginListener {
            override fun onSuccess(credentials: Credentials) {
                Assert.assertNotNull(credentials)
            }

            override fun onError(error: NuguOAuthError) {
                Assert.assertNotNull(error)
            }
        })
        listener.onSuccess(Credentials.getDefault())
        listener.onError(NuguOAuthError(throwable = RuntimeException()))
    }

    @Test
    fun testOnAccountListener() {
        val listener = NuguOAuth.OnceLoginListener(object : NuguOAuthInterface.OnAccountListener {
            override fun onSuccess(credentials: Credentials) {
                Assert.assertNotNull(credentials)
            }

            override fun onError(error: NuguOAuthError) {
                Assert.assertNotNull(error)
                Assert.assertEquals(error.throwable, RuntimeException::class)
            }
        })
        listener.onSuccess(Credentials.getDefault())
        listener.onError(NuguOAuthError(throwable = RuntimeException()))
    }

    @Test
    fun testMakeAuthorizeUri() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build(), "https://localhost")
        val uri = client.makeAuthorizeUri("{\"deviceSerialNumber\":\"deviceUniqueId\",\"theme\":\"dark\"}")
        Assert.assertNotNull(uri)
    }

    @Test
    fun testGenerateClientState() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build(), "https://localhost")
        val clientState = client.generateClientState()
        Assert.assertEquals(clientState, client.clientState)
        Assert.assertTrue(client.verifyState(clientState))
        Assert.assertFalse(client.verifyState("dummy"))

    }

    @Test
    fun testGetOptions() {
        val client = NuguOAuth("https://localhost")
        Assert.assertNull(client.getOptions())
    }

    @Test
    fun testSetAuthorization() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "tokenType1",
                scope = "device:S.I.D."
            )
        )
        Assert.assertEquals(client.getCredentials().tokenType, "tokenType1")
        client.setAuthorization("tokenType2","accessToken")
        Assert.assertEquals(client.getCredentials().tokenType, "tokenType2")
    }

    @Test
    fun testClearAuthorization() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "tokenType1",
                scope = "device:S.I.D."
            )
        )
        Assert.assertEquals(client.getCredentials().tokenType, "tokenType1")
        client.clearAuthorization()
        Assert.assertEquals(client.getCredentials().tokenType, "")
    }

    @Test
    fun testTokens() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "tokenType1",
                scope = "device:S.I.D."
            )
        )
        Assert.assertEquals(client.getRefreshToken(), "refreshToken")
        Assert.assertEquals(client.getExpiresInMillis(), 10L * 1000)
        Assert.assertNotNull(client.getIssuedTime())
        Assert.assertEquals(client.getScope(), "device:S.I.D.")
    }

    @Test
    fun testIsLogin() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "tokenType1",
                scope = "device:S.I.D."
            )
        )
        Assert.assertTrue(client.isLogin())
    }

    @Test
    fun testIsAnonymouslyLogin() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "tokenType1",
                scope = "device:S.I.D."
            )
        )
        Assert.assertTrue(client.isAnonymouslyLogin())
    }

    @Test
    fun testIsTidLogin() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "tokenType1",
                scope = "device:S.I.D."
            )
        )
        Assert.assertTrue(client.isTidLogin())
    }

    @Test
    fun testLoginWithTid() {
        val activity = Activity()
        val client = Mockito.mock(NuguOAuthInterface::class.java)
        verify(client, never()).loginWithTid(activity, object : NuguOAuthInterface.OnLoginListener {
            override fun onSuccess(credentials: Credentials) {
            }

            override fun onError(error: NuguOAuthError) {
            }
        })
    }
    @Test
    fun testAccountWithTid() {
        val activity = Activity()
        val client = Mockito.mock(NuguOAuthInterface::class.java)
        verify(client, never()).accountWithTid(activity, object : NuguOAuthInterface.OnAccountListener {
            override fun onSuccess(credentials: Credentials) {
            }

            override fun onError(error: NuguOAuthError) {
            }
        })
    }
    @Test
    fun testLoginAnonymously() {
        val client = Mockito.mock(NuguOAuthInterface::class.java)
        verify(client, never()).loginAnonymously(object : NuguOAuthInterface.OnLoginListener {
            override fun onSuccess(credentials: Credentials) {
            }

            override fun onError(error: NuguOAuthError) {
            }
        })
    }
    @Test
    fun testLoginSilentlyWithTid() {
        val client = Mockito.mock(NuguOAuthInterface::class.java)
        verify(client, never()).loginSilentlyWithTid("refreshToken", object : NuguOAuthInterface.OnLoginListener {
            override fun onSuccess(credentials: Credentials) {
            }

            override fun onError(error: NuguOAuthError) {
            }
        })
    }
    @Test
    fun testLoginWithAuthenticationCode() {
        val client = Mockito.mock(NuguOAuthInterface::class.java)
        verify(client, never()).loginWithAuthenticationCode("code", object : NuguOAuthInterface.OnLoginListener {
            override fun onSuccess(credentials: Credentials) {
            }

            override fun onError(error: NuguOAuthError) {
            }
        })
    }

    @Test
    fun testLoginWithDeviceCode() {
        val client = Mockito.mock(NuguOAuthInterface::class.java)
        verify(client, never()).loginWithDeviceCode("code", object : NuguOAuthInterface.OnLoginListener {
            override fun onSuccess(credentials: Credentials) {
            }
            override fun onError(error: NuguOAuthError) {
            }
        })
    }

    @Test
    fun testStartDeviceAuthorization() {
        val client = Mockito.mock(NuguOAuthInterface::class.java)
        verify(client, never()).startDeviceAuthorization("data", object : NuguOAuthInterface.OnDeviceAuthorizationListener {
            override fun onSuccess(result: DeviceAuthorizationResult) {
            }
            override fun onError(error: NuguOAuthError) {
            }
        })
    }


    @Test
    fun testRequestMe() {
        val client = Mockito.mock(NuguOAuthInterface::class.java)
        verify(client, never()).requestMe(object : NuguOAuthInterface.OnMeResponseListener {
            override fun onSuccess(response: MeResponse) {
            }

            override fun onError(error: NuguOAuthError) {
            }
        })
    }

    @Test
    fun testIntrospect() {
        val client = Mockito.mock(NuguOAuthInterface::class.java)
        verify(client, never()).introspect(object : NuguOAuthInterface.OnIntrospectResponseListener {
            override fun onSuccess(response: IntrospectResponse) {
            }
            override fun onError(error: NuguOAuthError) {
            }
        })
    }

    @Test
    fun testRevoke() {
        val client = Mockito.mock(NuguOAuthInterface::class.java)
        verify(client, never()).revoke(object : NuguOAuthInterface.OnRevokeListener {
            override fun onSuccess() {
            }
            override fun onError(error: NuguOAuthError) {
            }
        })
    }

    @Test
    fun testAuthDelegate() {
        val delegate = Mockito.mock(AuthDelegate::class.java)
        verify(delegate, never()).getAuthorization()
    }

    @Test
    fun testGetDeviceUniqueId() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "tokenType1",
                scope = "device:S.I.D."
            )
        )
        Assert.assertEquals(client.deviceUniqueId(), "device1")
    }

    @Test(expected = ClientUnspecifiedException::class)
    fun testCheckClientId() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .clientId("YOUR_CLIENT_ID_HERE")
            .build())
        client.checkClientId()
    }

    @Test(expected = ClientUnspecifiedException::class)
    fun testCheckClientSecret() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .clientSecret("YOUR_CLIENT_SECRET_HERE")
            .build())
        client.checkClientSecret()
    }

    @Test(expected = ClientUnspecifiedException::class)
    fun testCheckRedirectUri() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .redirectUri("YOUR REDIRECT URI SCHEME://YOUR REDIRECT URI HOST")
            .build())
        client.checkRedirectUri()
    }

    @Test
    fun testSetResult() {
        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "tokenType1",
                scope = "device:S.I.D."
            )
        )
        client.onceLoginListener = NuguOAuth.OnceLoginListener(object : NuguOAuthInterface.OnLoginListener {
            override fun onSuccess(credentials: Credentials) {
                Assert.assertNotNull(credentials)
            }

            override fun onError(error: NuguOAuthError) {
                Assert.assertNotNull(error)
            }
        })
        client.setResult(true)
        client.setResult(false, NuguOAuthError(throwable = RuntimeException()))
    }

    @Test
    fun testCodeFromIntent() {
        ConfigurationStore.configure("{\n" +
                "    \"OAuthServerUrl\": \"https://api.sktnugu.com\",\n" +
                "    \"OAuthClientId\": \"app.nugu.test\",\n" +
                "    \"OAuthClientSecret\": \"12121212-eee-ddd-ccc-12121212\",\n" +
                "    \"OAuthRedirectUri\": \"nugu.user.app.nugu.test://auth\",\n" +
                "    \"PoCId\": \"app.nugu.test\",\n" +
                "    \"DeviceTypeCode\": \"APP\"\n" +
                "}")

        val client = NuguOAuth.create(options = NuguOAuthOptions.Builder()
            .deviceUniqueId("device1")
            .build())
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "tokenType1",
                scope = "device:S.I.D."
            )
        )

        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            NuguOAuthCallbackActivity::class.java
        ).apply {
            data = Uri.parse("https://dummy.com?code=testcode")
        }
        Assert.assertNotNull(client.codeFromIntent(intent))
    }

    @Test
    fun testGetLoginUri() {
        ConfigurationStore.configure(
            "{\n" +
                    "    \"OAuthServerUrl\": \"https://api.sktnugu.com\",\n" +
                    "    \"OAuthClientId\": \"app.nugu.test\",\n" +
                    "    \"OAuthClientSecret\": \"12121212-eee-ddd-ccc-12121212\",\n" +
                    "    \"OAuthRedirectUri\": \"nugu.user.app.nugu.test://auth\",\n" +
                    "    \"PoCId\": \"app.nugu.test\",\n" +
                    "    \"DeviceTypeCode\": \"APP\"\n" +
                    "}"
        )

        val client = NuguOAuth.create(
            options = NuguOAuthOptions.Builder()
                .deviceUniqueId("device1")
                .build()
        )
        client.setCredentials(
            Credentials(
                accessToken = "accessToken",
                refreshToken = "refreshToken",
                expiresIn = 10L,
                issuedTime = Date().time,
                tokenType = "tokenType1",
                scope = "device:S.I.D."
            )
        )
        Assert.assertNotNull(
            client.getLoginUri("{\"deviceSerialNumber\":\"deviceUniqueId\",\"theme\":\"dark\"}")
        )
    }
}