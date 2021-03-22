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

package com.skt.nugu.sdk.agent.ext.phonecall.payload

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert
import org.junit.Test

class SendCandidatesPayloadTest {

    private fun createBasePayload(): JsonObject = JsonObject().apply {
        addProperty("playServiceId", "playServiceId")
        addProperty("intent", "CALL")
    }

    @Test
    fun testParseBasicPayload() {
        val payload = SendCandidatesPayload.fromJson(createBasePayload().toString())
        Assert.assertTrue(payload != null)
    }

    @Test
    fun testParseSearchTargetListContainingInvalidValue() {
        val payload = SendCandidatesPayload.fromJson(createBasePayload().apply {
            add("searchTargetList", JsonArray().apply {
                add("CONTACT")
                add("EXCHANGE")
                add("T114")
                add("INVALID")
            })
        }.toString())

        Assert.assertTrue(payload?.searchTargetList?.size == 3)
    }
}