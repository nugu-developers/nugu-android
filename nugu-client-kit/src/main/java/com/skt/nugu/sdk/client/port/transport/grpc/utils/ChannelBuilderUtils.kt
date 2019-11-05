package com.skt.nugu.sdk.client.port.transport.grpc.utils

import com.skt.nugu.sdk.client.port.transport.grpc.Options
import com.skt.nugu.sdk.client.port.transport.grpc.HeaderClientInterceptor
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.SdkVersion
import io.grpc.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class ChannelBuilderUtils {
    companion object {
        fun createChannelBuilderWith(
            options: Options,
            authorization: String
        ): ManagedChannelBuilder<*> {
            val builder = ManagedChannelBuilder
                .forAddress(options.address, options.port)
                .userAgent(userAgent())

            if (!options.hostname.isBlank()) {
                builder.overrideAuthority(options.hostname)
            }
            if (options.debug) {
                // adb shell setprop log.tag.io.grpc.ChannelLogger DEBUG
                builder.maxTraceEvents(100)
                val logger = java.util.logging.Logger.getLogger(ChannelLogger::class.java.name)
                logger.level = Level.ALL
            }
            return builder.intercept(
                HeaderClientInterceptor(
                    authorization
                )
            )
        }

        private fun userAgent(): String {
            return "OpenSDK/" + SdkVersion.currentVersion
        }

        fun shutdown(channel : ManagedChannel?) {
            var isTerminated = false
            channel?.apply {
                try {
                    if (isShutdown) {
                        isTerminated = true
                        return@apply
                    }
                    Logger.d("DeviceGatewayTransport","awaitTermination begin")
                    isTerminated = shutdown().awaitTermination(10, TimeUnit.SECONDS)
                    Logger.d("DeviceGatewayTransport","awaitTermination end")
                } catch (e: Throwable) {
                    Logger.d("DeviceGatewayTransport","awaitTermination ${e.message}")
                } finally {
                    if(!isTerminated) {
                        shutdownNow()
                        Logger.d("DeviceGatewayTransport","awaitTermination shutdownNow")
                    }
                }
            }
        }
    }
}