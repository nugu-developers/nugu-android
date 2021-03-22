/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.sound

import com.google.gson.JsonObject
import org.junit.Assert
import org.junit.Test

class BeepDirectiveParseTest {
    @Test
    fun testParseInvalidBeepName() {
        val jsonPayload = JsonObject().apply {
            addProperty("playServiceId","playServiceId")
            addProperty("beepName", "RESPONSE_FAI")
        }

        val payload = BeepDirective.Payload.fromJson(jsonPayload.toString())
        Assert.assertTrue(payload == null)
    }

    @Test
    fun testParseValidBeepName() {
        val jsonPayload = JsonObject().apply {
            addProperty("playServiceId","playServiceId")
            addProperty("beepName", "RESPONSE_FAIL")
        }

        val payload = BeepDirective.Payload.fromJson(jsonPayload.toString())
        Assert.assertTrue(payload != null)
    }
}