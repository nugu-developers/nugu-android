/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.client.port.transport.grpc.utils

import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.utils.Logger
import io.grpc.*

/**
 *  Implementation of ClientInterceptor
 **/
internal class HeaderClientInterceptor(val authDelegate: AuthDelegate) : ClientInterceptor {
    companion object {
        private const val TAG = "HeaderClientInterceptor"
        internal val AUTH_TOKEN_KEY: Metadata.Key<String> =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
    }

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions, next: Channel
    ): ClientCall<ReqT, RespT> {
        return object :
            ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {

            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                headers.put(AUTH_TOKEN_KEY, authDelegate.getAuthorization())

                super.start(object :
                    ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    override fun onHeaders(headers: Metadata?) {
                        /**
                         * if you don't need receive header from server,
                         * you can use [io.grpc.stub.MetadataUtils.attachHeaders]
                         * directly to send header
                         */
                        Logger.d(TAG, "header received from server:$headers")
                        super.onHeaders(headers)
                    }
                }, headers)
            }
        }
    }
}
