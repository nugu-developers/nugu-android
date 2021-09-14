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

class CredentialsTest {
    @Test
    fun `Parse JSON with Credentials`() {
        val json = Credentials(
            accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9",
            refreshToken = "",
            expiresIn = 10L,
            issuedTime = Date().time,
            tokenType = "Bearer",
            scope = "device:S.I.D."
        ).toString()
        Assert.assertTrue(json.isNotEmpty())

        val credentials = Credentials.parse(json)
        Assert.assertNotNull(credentials)
        Assert.assertEquals(credentials.accessToken,"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9")
    }

    @Test(expected = JSONException::class)
    fun `JSONObject(access_token) not found`() {
        Credentials.parse(
            "{\n" +
                    "  \"expires_in\": \"1\",\n" +
                    "  \"token_type\": \"Bearer\",\n" +
                    "  \"jti\": \"d1d90f19-3ab5-42e9-ba6d-4c9b74b559db\"\n" +
                    "}"
        )
    }
    @Test
    fun `JSONObject empty`() {
        Assert.assertEquals(Credentials.parse(""), Credentials.getDefault())
    }
    @Test
    fun `JSONObject to string`() {
        val expected = "{\"access_token\":\"123\",\"refresh_token\":\"321\",\"scope\":\"scope\",\"token_type\":\"type\",\"issued_time\":2,\"expires_in\":1}"
        val credentials = Credentials(
            accessToken = "123",
            refreshToken = "321",
            expiresIn = 1L,
            issuedTime = 2L,
            tokenType = "type",
            scope = "scope"
        )
        Assert.assertEquals(credentials.toString(), expected)
    }

    @Test
    fun `Clear Credentials`() {
        val expected = "{\"access_token\":\"123\",\"refresh_token\":\"321\",\"scope\":\"scope\",\"token_type\":\"type\",\"issued_time\":2,\"expires_in\":1}"
        val credentials = Credentials(
            accessToken = "123",
            refreshToken = "321",
            expiresIn = 1L,
            issuedTime = 2L,
            tokenType = "type",
            scope = "scope"
        )
        Assert.assertEquals(credentials.toString(), expected)
        credentials.clear()
        Assert.assertEquals(credentials.accessToken, "")
    }

    @Test
    fun `Default constructor of Credentials`() {
        val credentials = Credentials.getDefault()
        Assert.assertNotNull(credentials)
        Assert.assertEquals(credentials.accessToken, "")
    }
}