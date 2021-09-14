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

import org.json.JSONException
import org.junit.Assert
import org.junit.Test
import java.util.*

class MeResponseTest {
    @Test
    fun `Parse JSON with MeResponse`() {
        val response1 = MeResponse(
            anonymous = true,
            deviceId = "dummy_deviceId",
            tid = "dummy_tid",
            userId = "dummy_userId"
        )
        Assert.assertNotNull(response1)
        val response2 = MeResponse.parse(
            "{\n" +
                     "  \"anonymous\": true,\n" +
                    "  \"deviceId\": \"dummy_deviceId\",\n" +
                    "  \"tid\": \"dummy_tid\",\n" +
                    "  \"userId\": \"dummy_userId\"\n" +
                    "}"
        )
        Assert.assertNotNull(response2)
        Assert.assertEquals(response1,response2)
    }

    @Test(expected = JSONException::class)
    fun `JSONObject(anonymous) not found`() {
        MeResponse.parse(
            "{\n" +
                   // "  \"anonymous\": true,\n" +
                    "  \"deviceId\": \"dummy_deviceId\",\n" +
                    "  \"tid\": \"dummy_tid\",\n" +
                    "  \"userId\": \"dummy_userId\"\n" +
                    "}"
        )
    }
}