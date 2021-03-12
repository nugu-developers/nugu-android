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
package com.skt.nugu.sdk.client.port.transport

import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.client.port.transport.grpc2.GrpcTransportFactory
import com.skt.nugu.sdk.client.port.transport.grpc2.NuguServerInfo
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate

/**
 * Creates the default transport.
 */
class DefaultTransportFactory {
    companion object {
        fun buildTransportFactory(authDelegate: AuthDelegate) =
            GrpcTransportFactory(NuguServerInfo(object : NuguServerInfo.Delegate {
                override fun getNuguServerInfo(): NuguServerInfo {
                    val metadata = ConfigurationStore.configurationMetadataSync()
                    return NuguServerInfo.Builder().deviceGW(metadata?.deviceGatewayServerGrpcUri)
                        .registry(metadata?.deviceGatewayRegistryUri)
                        .keepConnection(authDelegate.isSidSupported())
                        .build()
                }
            }))
    }
}