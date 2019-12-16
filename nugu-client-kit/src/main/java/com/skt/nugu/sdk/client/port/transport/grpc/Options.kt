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
package com.skt.nugu.sdk.client.port.transport.grpc

/**
 * Provides options for transport
 */
data class Options(
    /** Returns the address. */
    val address: String = "reg-http.sktnugu.com",
    /** Returns the hostname. */
    val hostname: String = "",
    /** Returns the port. */
    val port: Int = 443,
    /** Returns count of retry. */
    val retryCountLimit: Int = 0,
    /** Returns the connection timeout. */
    val connectionTimeout: Int = 0,
    /** Returns the enable of compression.*/
    val compressedConnection: Boolean = false,
    /** Returns the enable of debug */
    val debug: Boolean = true,
    /** Returns the charge */
    val charge: String = "Normal",
    /** Returns the protocol */
    val protocol: String = "H2_GRPC"
)