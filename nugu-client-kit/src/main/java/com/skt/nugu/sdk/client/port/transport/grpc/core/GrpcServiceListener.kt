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
package com.skt.nugu.sdk.client.port.transport.grpc.core

import devicegateway.grpc.PolicyResponse

internal interface GrpcServiceListener {
    /**
     * Receives a Policy from the device-gateway-registry.
     * @param policy is connection Policy information.
     */
    fun onRegistryConnected(policy: PolicyResponse)
    /**
     * Receives a HealthCheck result from the device-gateway
     * @param success true is success, false is fail
     */
    fun onPingRequestAcknowledged(success: Boolean)
    /**
     * Notification that a ping request timed out.
     */
    fun onPingTimeout()
    /**
     * Notification that a connect timed out.
     */
    fun onConnectTimeout()
    /**
     * Receives a Directive from the DeviceGateway.
     */
    fun onDirectives(directive : String)
}