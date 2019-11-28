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

import com.skt.nugu.sdk.client.port.transport.grpc.core.HeaderClientInterceptor
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.SdkVersion
import devicegateway.grpc.PolicyResponse
import io.grpc.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * Class to create and manage an GRPC Channels
 */
internal class Channels(val defaultOptions: Options) {
    private var currentOptions = defaultOptions.copy()
    private var managedChannel: ManagedChannel? = null
    private var channel: Channel? = null
    private var backoff = Backoff()
    private var callback: Runnable? = null
    var serverPolicyList = ConcurrentLinkedQueue<PolicyResponse.ServerPolicy>()
    var compressorName: String = "gzip"

    companion object {
        private const val TAG = "GrpcTransport"

        /**
         * Create an instance of Channels
         */
        fun newChannel(opts: Options): Channels {
            return Channels(opts)
        }
    }

    /**
     * Returns the current [Options]
     */
    fun getOptions() = currentOptions

    /**
     * Returns a string that User-Agent
     */
    private fun userAgent(): String {
        return "OpenSDK/" + SdkVersion.currentVersion
    }

    /**
     * connection to DeviceGateway
     * @return this(Channels) object
     */
    fun connect(callback: Runnable, authorization : String): Channels {
        val builder = ManagedChannelBuilder
            .forAddress(currentOptions.address, currentOptions.port)
            .userAgent(userAgent())
        if(!currentOptions.hostname.isNullOrBlank()) {
            builder.overrideAuthority(currentOptions.hostname)
        }
        if (currentOptions.debug) {
            // adb shell setprop log.tag.io.grpc.ChannelLogger DEBUG
            builder.maxTraceEvents(100)
            val logger = java.util.logging.Logger.getLogger(ChannelLogger::class.java.name)
            logger.level = Level.ALL
        }
        if(this.managedChannel != null) {
            Logger.e(TAG, "[connect] managedChannel is $managedChannel")
        }
        this.managedChannel = builder.build()
        this.callback = callback
        this.channel = ClientInterceptors.intercept(managedChannel,
            HeaderClientInterceptor(authorization)
        )
        waitForChannelStateChange(managedChannel?.getState(true), callback)
        return this
    }

    /**
     *  waiting for connection state changed
     */
    private fun waitForChannelStateChange(oldState: ConnectivityState?, callback: Runnable?) {
        managedChannel?.notifyWhenStateChanged(oldState) {
            val newState = getState(true)
            Logger.d(TAG, "Entering $newState state")

            if(newState == ConnectivityState.TRANSIENT_FAILURE) {
                this.callback?.run()
                this.callback = null
                return@notifyWhenStateChanged
            }

            this.waitForChannelStateChange(newState, null)
            callback?.run()
        }
    }

    /**
     * @return grpc(Channel) object
     */
    fun getChannel(): Channel? {
        return channel
    }

    /**
     * Gets the Backoff instance
     */
    fun getBackoff(): Backoff {
        return backoff
    }

    /**
     * Returns whether the channel is shutdown. Shutdown channels immediately cancel any new calls,
     * but may still have some calls being processed.
     * @see #shutdown()
     * @see #isTerminated()
     */
    fun isShutdown() : Boolean {
        if(null == managedChannel) {
            return true
        }
        return managedChannel!!.isShutdown
    }

    /**
     * Gets the current connectivity state.
     * @param requestConnection if {@code true}, the channel will try to make a connection if it is currently IDLE
     */
    fun getState(requestConnection: Boolean): ConnectivityState {
        if (managedChannel == null) {
            return ConnectivityState.IDLE
        }
        return managedChannel!!.getState(requestConnection)
    }

    /**
     * Explicitly clean up resources.
     */
    fun shutdown() {
        this.callback = null
        var isTerminated = false
        managedChannel?.apply {
            try {
                if (isShutdown) {
                    isTerminated = true
                    return@apply
                }
                isTerminated = shutdown().awaitTermination(1, TimeUnit.SECONDS)
            } catch (e: Throwable) {
                Logger.e(TAG, "Error shutdown the gRPC channel. ${e.message}")
            } finally {
                if(!isTerminated) {
                    shutdownNow()
                }
                managedChannel = null
            }
        }
    }

    /**
     * Sets next channel
     * @return true is success, otherwise false
     */
    fun nextChannel() : Boolean {
        val policy = serverPolicyList.poll() ?: return false
        return this.nextChannel(policy)
    }

    /**
     * Sets next channel
     * @param policy Network connection policy provided by the registry
     * @return true is success, otherwise false
     */
    private fun nextChannel( policy: PolicyResponse.ServerPolicy ) : Boolean {
        currentOptions = Options(
            address = policy.address,
            port = policy.port,
            retryCountLimit = policy.retryCountLimit,
            connectionTimeout = policy.connectionTimeout,
            hostname = policy.hostName
        )
        with(backoff) {
            maxAttempts = policy.retryCountLimit
            reset()
        }
        return true
    }

    /**
     * Reset options to the default for connect the registry
     */
    fun resetChannel() {
        // setup default
        currentOptions = defaultOptions.copy()
    }

    /**
     * Sets policy
     * @param policy Network connection policy provided by the registry
     */
    fun setPolicy(policy: PolicyResponse) {
        this.serverPolicyList = ConcurrentLinkedQueue(policy.serverPolicyList)
        policy.healthCheckPolicy.apply {
            backoff.healthCheckTimeout = this.healthCheckTimeout.toLong()
            backoff.retryDelay = this.retryDelay.toLong()
            backoff.maxAttempts = this.retryCountLimit
        }
    }
}