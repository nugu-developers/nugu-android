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

import org.junit.Assert.*

import junit.framework.TestCase
import org.json.JSONException
import org.junit.Assert
import org.junit.Test

class DeviceAuthorizationResultTest {
    @Test
    fun `Parse JSON with DeviceAuthorizationResult`() {
        val response1 = DeviceAuthorizationResult(
            device_code = "dummy_device_code",
            user_code = "dummy_user_code",
            verification_uri = "dummy_verification_uri",
            verification_uri_complete = "dummy_verification_uri_complete",
            expires_in = 1000,
            interval = 1000
        )
        Assert.assertNotNull(response1)
        val response2 = DeviceAuthorizationResult.parse(
            "{\n" +
                    "  \"device_code\": \"dummy_device_code\",\n" +
                    "  \"user_code\": \"dummy_user_code\",\n" +
                    "  \"verification_uri\": \"dummy_verification_uri\",\n" +
                    "  \"verification_uri_complete\": \"dummy_verification_uri_complete\",\n" +
                    "  \"expires_in\": 1000,\n" +
                    "  \"interval\": 1000\n" +
                    "}"
        )
        Assert.assertNotNull(response2)
        Assert.assertEquals(response1, response2)
    }

    @Test(expected = JSONException::class)
    fun `JSONObject(device_code) not found`() {
        /**
         *
        device_code = getString("device_code"),
        user_code = getString("user_code"),
        verification_uri = getString("verification_uri"),
        verification_uri_complete = getString("verification_uri_complete"),
        expires_in = getLong("expires_in"),
        interval = getLong("interval")
         */
        DeviceAuthorizationResult.parse(
            "{\n" +
                    // "  \"device_code\": \"dummy_device_code\",\n" +
                    "  \"user_code\": \"dummy_user_code\",\n" +
                    "  \"verification_uri\": \"dummy_verification_uri\",\n" +
                    "  \"verification_uri_complete\": \"dummy_verification_uri_complete\",\n" +
                    "  \"expires_in\": 1000,\n" +
                    "  \"interval\": 1000\n" +
                    "}"
        )
    }
}