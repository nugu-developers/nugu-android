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
package com.skt.nugu.sdk.client.port.transport.http2.interceptors

import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class SecurityInterceptor(private val authDelegate: AuthDelegate) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        builder.header("Authorization", authDelegate.getAuthorization().toString())

        return try {
            chain.proceed(builder.build())
        } catch (e: IllegalStateException) {
            throw IOException(e)
        }
    }
}