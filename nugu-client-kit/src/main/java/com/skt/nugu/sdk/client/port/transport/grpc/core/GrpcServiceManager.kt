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

import com.skt.nugu.sdk.client.port.transport.grpc.Channels
import java.util.concurrent.ConcurrentHashMap

/**
 * A manager to manage and create grpc service
 */
internal class GrpcServiceManager {
    /** RPC services **/
    private val services = ConcurrentHashMap<Class<*>, GrpcServiceInterface>()
    /** A kind of server*/
    enum class SERVER { REGISTRY, DEVICEGATEWAY }

    /**
     * Registers a service.
     */
    fun addServices(listener: GrpcServiceListener, service : SERVER) {
        this.shutdown()
        when(service) {
            SERVER.REGISTRY -> {
                services.putIfAbsent(RegistryService::class.java, RegistryService(listener))
            }
            SERVER.DEVICEGATEWAY -> {
                // set the deviceGateway as a service list
                services.putIfAbsent(PingService::class.java, PingService(listener))
                services.putIfAbsent(EventStreamService::class.java, EventStreamService(listener))
                services.putIfAbsent(CrashReportService::class.java, CrashReportService(listener))
            }
        }
    }

    /**
     * Returns whether this object is currently registered with a service.
     */
    fun hasService(server: SERVER): Boolean {
        return when (server) {
            SERVER.REGISTRY -> {
                has<RegistryService>()
            }
            SERVER.DEVICEGATEWAY -> {
                has<PingService>() &&
                        has<EventStreamService>() &&
                        has<CrashReportService>()
            }
        }
    }

    /**
     * Initiate a connection to service.
     */
    fun connect(channel: Channels) = services.forEach {
        it.value.connect(channel)
    }

    /**
     * Shutdown from service.
     * This method can explicitly clean up client resources.
     */
    fun shutdown() {
        services.forEach { it.value.shutdown() }
        services.clear()
    }

    /**
     * Returns eventstream service registered
     */
    fun getEvent() : EventStreamService? = get()

    /**
     * Returns crashreport service registered
     */
    fun getCrashReport() : CrashReportService? = get()

    /**
     * Returns ping service registered
     */
    fun getPing() : PingService?  = get()

    /**
     * This is a helper method which get an service
     * @param cls is javaclass
     **/
    fun get(cls: Class<*>): Any? = services[cls]
    /**
     * This is the helper method where the service exists.
     **/
    inline fun <reified T : Any> has() : Boolean  = get(T::class.java) != null

    /**
     * This is a helper method which get an service
     **/
    inline fun <reified T : Any> get() : T? = get(T::class.java) as? T
}
