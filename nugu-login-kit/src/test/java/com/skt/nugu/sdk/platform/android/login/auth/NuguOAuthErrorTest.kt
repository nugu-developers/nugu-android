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

import android.content.ActivityNotFoundException
import com.skt.nugu.sdk.platform.android.login.exception.BaseException
import junit.framework.TestCase
import org.junit.Assert
import org.junit.Test
import java.lang.RuntimeException
import java.net.UnknownHostException

class NuguOAuthErrorTest : TestCase() {
    @Test
    fun testOAuthErrors() {
        val oAuthError1 = NuguOAuthError(throwable = ActivityNotFoundException())
        Assert.assertTrue(oAuthError1.error == NuguOAuthError.ACTIVITY_NOT_FOUND_ERROR)
        val oAuthError2 = NuguOAuthError(throwable = UnknownHostException())
        Assert.assertTrue(oAuthError2.error == NuguOAuthError.NETWORK_ERROR)
        val oAuthError3 = NuguOAuthError(throwable = SecurityException())
        Assert.assertTrue(oAuthError3.error == NuguOAuthError.SECURITY_ERROR)
        val oAuthError4 = NuguOAuthError(throwable = UninitializedPropertyAccessException())
        Assert.assertTrue(oAuthError4.error == NuguOAuthError.INITIALIZE_ERROR)
        val oAuthError5 = NuguOAuthError(throwable = RuntimeException())
        Assert.assertTrue(oAuthError5.error == NuguOAuthError.UNKNOWN_ERROR)
        val oAuthError6 = NuguOAuthError(throwable = BaseException.HttpErrorException(404, "not found"))
        Assert.assertTrue(oAuthError6.error == NuguOAuthError.NETWORK_ERROR)
        Assert.assertTrue(oAuthError6.httpCode == 404)
        Assert.assertTrue(oAuthError6.description == "not found")
        Assert.assertNotEquals(oAuthError6, oAuthError2)

        val oAuthError7 = NuguOAuthError(throwable = BaseException.HttpErrorException(500, "internal server error"))
        Assert.assertTrue(oAuthError7.error == NuguOAuthError.NETWORK_ERROR)
        Assert.assertTrue(oAuthError7.httpCode == 500)

        val oAuthError8 =
            NuguOAuthError(throwable = BaseException.UnAuthenticatedException("error", "description", "code"))
        Assert.assertTrue(oAuthError8.code == "code")
    }

    @Test
    fun testToString() {
        val oAuthError1 = NuguOAuthError(throwable = ActivityNotFoundException())
        Assert.assertNotNull(oAuthError1.toString())
    }
}