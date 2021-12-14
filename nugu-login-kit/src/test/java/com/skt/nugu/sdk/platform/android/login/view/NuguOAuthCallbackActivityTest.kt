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

package com.skt.nugu.sdk.platform.android.login.view

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.platform.android.login.auth.Credentials
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth.Companion.ACTION_LOGIN
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth.Companion.EXTRA_OAUTH_ACTION
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthInterface
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthOptions
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.IllegalStateException
import java.util.*

@RunWith(AndroidJUnit4::class)
class NuguOAuthCallbackActivityTest {
    @Test
    fun testAlreadyTidLogIn() {
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
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            NuguOAuthCallbackActivity::class.java
        ).apply {
            putExtra(EXTRA_OAUTH_ACTION, ACTION_LOGIN)
        }
        val activity = NuguOAuthCallbackActivity()
        Assert.assertFalse(activity.processOAuthCallback(intent))
    }

    @Test
    fun testNuguOAuthNotInitialized() {
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            NuguOAuthCallbackActivity::class.java
        ).apply {
            putExtra(EXTRA_OAUTH_ACTION, ACTION_LOGIN)
        }
        val activity = NuguOAuthCallbackActivity()
        Assert.assertFalse(activity.processOAuthCallback(intent))
    }

    @Test
    fun testActionIsEmpty() {
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
        val intent = Intent(
            ApplicationProvider.getApplicationContext(),
            NuguOAuthCallbackActivity::class.java
        )
        val activity = NuguOAuthCallbackActivity()
        Assert.assertFalse(activity.processOAuthCallback(intent))
    }
}