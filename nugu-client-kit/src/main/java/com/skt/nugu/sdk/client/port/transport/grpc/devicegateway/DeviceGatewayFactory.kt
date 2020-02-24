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
package com.skt.nugu.sdk.client.port.transport.grpc.devicegateway

import com.skt.nugu.sdk.client.port.transport.grpc.devicegateway.h2.HTTP2DeviceGatewayClient
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import devicegateway.grpc.PolicyResponse
import com.skt.nugu.sdk.client.port.transport.grpc.Protocol
import com.skt.nugu.sdk.client.port.transport.grpc.devicegateway.grpc.GrpcDeviceGatewayClient

class DeviceGatewayFactory(val protocol: Protocol) {
    fun create(
        policyResponse: PolicyResponse,
        messageConsumer: MessageConsumer?,
        transportObserver: DeviceGateway.Observer?,
        authDelegate: AuthDelegate
    ): DeviceGateway {
        when (protocol) {
            Protocol.GRPC -> {
                return GrpcDeviceGatewayClient.create(
                    policyResponse,
                    messageConsumer,
                    transportObserver,
                    authDelegate
                )
            }
            Protocol.HTTP2 -> {
                return HTTP2DeviceGatewayClient.create(
                    policyResponse,
                    messageConsumer,
                    transportObserver,
                    authDelegate
                )
            }
        }

    }
}