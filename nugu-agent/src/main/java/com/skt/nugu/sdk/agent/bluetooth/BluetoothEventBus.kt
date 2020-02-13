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
package com.skt.nugu.sdk.agent.bluetooth

import java.util.concurrent.ConcurrentHashMap

/**
 * A class that provides event publication and subscription for Bluetooth.
 **/
internal class BluetoothEventBus {
    private val callbacks = ConcurrentHashMap<Int, ConcurrentHashMap<String, Listener>>()

    interface Listener {
        fun call(name: String) : Boolean { return false }
        fun call(name: String, value: Any) : Boolean { return false }
    }

    /**
     * Listens on the event.
     * @param names an event names.
     * @return a reference to this object.
     * Note: Make sure to call unsubscribe(Int) to avoid memory leaks
     */
    fun subscribe(names: ArrayList<String>, listener: Listener): BluetoothEventBus {
        if (this.callbacks[names.hashCode()] == null) {
            this.callbacks.putIfAbsent(names.hashCode(), ConcurrentHashMap())
        }
        this.callbacks[names.hashCode()]?.apply {
            for (name in names) {
                putIfAbsent(name, listener)
            }
        }
        return this
    }

    /**
     * Removes all subscribed listeners.
     */
    fun clearAllSubscribers() {
        this.callbacks.clear()
    }

    /**
     * Remove a listener.
     * @param hashCode the key of callbacks
     * @return a reference to this object.
     */
    private fun unsubscribe(hashCode: Int): BluetoothEventBus {
        this.callbacks.remove(hashCode)
        return this
    }

    /**
     * Executes each of listeners
     * @param name an event name.
     * @return a reference to this object.
     */
    fun post(name: String): Boolean {
        this.callbacks.forEach {
            for (callback in it.value) {
                if (callback.key == name) {
                    callback.value.call(name).let {result->
                        unsubscribe(it.key)
                        return result
                    }
                }
            }
        }
        return false
    }

    fun post(name: String, value: Any): Boolean {
        this.callbacks.forEach {
            for (callback in it.value) {
                if (callback.key == name) {
                    callback.value.call(name, value).let { result ->
                        unsubscribe(it.key)
                        return result
                    }
                }
            }
        }
        return false
    }
}