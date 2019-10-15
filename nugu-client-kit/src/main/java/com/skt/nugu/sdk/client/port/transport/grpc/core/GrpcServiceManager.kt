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
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.KParameter

internal class GrpcServiceManager {
    // RPC services
    private val services = ConcurrentHashMap<KClass<*>, GrpcServiceInterface>()
    enum class SERVER { REGISTRY, DEVICEGATEWAY }

    // Add a service to the list of services
    fun addServices(listener: GrpcServiceListener, service : SERVER) {
        this.shutdown()

        when(service) {
            SERVER.REGISTRY -> {
                // set the registry as a service list
                addService<RegistryService>(listener)
            }
            SERVER.DEVICEGATEWAY -> {
                // set the deviceGateway as a service list
                addService<PingService>(listener)
                addService<EventStreamService>(listener)
                addService<CrashReportService>(listener)
            }
        }
    }

    fun hasService(server: SERVER): Boolean {
        when (server) {
            SERVER.REGISTRY -> {
                return has<RegistryService>()
            }
            SERVER.DEVICEGATEWAY -> {
                return has<PingService>() &&
                        has<EventStreamService>() &&
                        has<CrashReportService>()
            }
        }
    }


    fun connect(channel: Channels) = services.forEach {
        it.value.connect(channel)
    }

    // Explicitly clean up client resources.
    fun shutdown() {
        services.forEach { it.value.shutdown() }
        services.clear()
    }

    private inline fun <reified T : Any> addService(observer: GrpcServiceListener) {
        addService(T::class, observer )
    }

    // Creating New Class Instances
    private fun addService(kClass: KClass<*>, vararg args: Any) {
        val constructor = kClass.primaryConstructor ?: return
        constructor.isAccessible = true
        val argParameters = ConcurrentHashMap<KParameter, Any>(2)
        val constructorParameters = constructor.parameters
        for (index in constructorParameters.indices) {
            argParameters.putIfAbsent(constructorParameters[index], args[index])
        }
        services.putIfAbsent(kClass, constructor.callBy(argParameters) as GrpcServiceInterface)
    }

    fun getEvent() : EventStreamService? = get()
    fun getCrashReport() : CrashReportService? = get()
    fun getPing() : PingService?  = get()

    fun get(kClass: KClass<*>): Any? = services[kClass]

    inline fun <reified T : Any> has() : Boolean  = get(T::class) != null
    inline fun <reified T : Any> get() : T? = get(T::class) as? T
}
