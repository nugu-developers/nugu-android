/**
 * Copyright (c) 2022 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.ext

import com.google.gson.GsonBuilder
import com.skt.nugu.sdk.agent.ext.phonecall.Contact
import org.junit.Assert
import org.junit.Test

class ContactTest {
    private val gson = GsonBuilder().registerTypeAdapter(Contact::class.java, Contact.gsonSerializer).create()
    private val testJsonString = "{\"label\":\"HOME\",\"number\":\"010-123-4567\",\"isBlocked\":\"TRUE\"}"
    private val testContact = Contact(Contact.Label.HOME, "010-123-4567", true)

    @Test
    fun contactSerializeTest() {
        val jsonObject = gson.toJsonTree(testContact).asJsonObject
        Assert.assertEquals(jsonObject.get("label").asString, "HOME")
        Assert.assertEquals(jsonObject.get("number").asString, "010-123-4567")
        Assert.assertEquals(jsonObject.get("isBlocked").asString, "TRUE")
    }
}