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

import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.http.RealInterceptorChain

class ForwardInterceptor : Interceptor {
    override fun intercept (chain: Interceptor.Chain): Response {
        val realChain = chain as RealInterceptorChain
        val originalResponse = chain.proceed (chain.request())
        if(originalResponse.request.tag() is Callback) {
            val callback = originalResponse.request.tag() as Callback
            callback.onResponse(realChain.call(), originalResponse)
        }
        return originalResponse
    }
}