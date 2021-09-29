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

package com.skt.nugu.sdk.core.display

import com.skt.nugu.sdk.core.interfaces.display.InterLayerDisplayPolicyManager
import com.skt.nugu.sdk.core.interfaces.display.LayerType
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class InterLayerDisplayPolicyManagerImpl: InterLayerDisplayPolicyManager {
    companion object {
        private const val TAG = "InterLayerDisplayPolicyManager"
    }

    private val lock = ReentrantLock()
    private val displayLayers = HashSet<InterLayerDisplayPolicyManager.DisplayLayer>()
    private val refreshFutureMap = HashMap<InterLayerDisplayPolicyManager.PlayLayer, MutableList<Pair<InterLayerDisplayPolicyManager.DisplayLayer, Future<*>>>>()
    private val displayRefreshScheduler = Executors.newSingleThreadScheduledExecutor()
    private val listeners = CopyOnWriteArraySet<InterLayerDisplayPolicyManager.Listener>()

    private fun isEvaporatableLayer(layerType: LayerType): Boolean =  when(layerType) {
        LayerType.MEDIA,
        LayerType.CALL,
        LayerType.NAVI -> false
        else -> true
    }

    override fun onDisplayLayerRendered(layer: InterLayerDisplayPolicyManager.DisplayLayer) {
        lock.withLock {
            if(displayLayers.contains(layer)) {
                Logger.d(TAG, "[onDisplayLayerRendered] already called: $layer")
                return@withLock
            }

            displayLayers.filter {
                isEvaporatableLayer(it.getLayerType())
                        && (layer.historyControl?.parentToken != it.token || it.token == null)
            }.forEach {
                Logger.d(TAG, "[onDisplayLayerRendered] clear: $it")
                it.clear()
            }

            displayLayers.add(layer)

            listeners.forEach {
                it.onDisplayLayerRendered(layer)
            }
            Logger.d(TAG, "[onDisplayLayerRendered] rendered: $layer")
        }
    }

    override fun onDisplayLayerCleared(layer: InterLayerDisplayPolicyManager.DisplayLayer) {
        lock.withLock {
            val removed = displayLayers.remove(layer)

            if(removed) {
                listeners.forEach {
                    it.onDisplayLayerCleared(layer)
                }
            }
            Logger.d(TAG, "[onDisplayLayerCleared] clear: $layer, removed: $removed")
        }
    }

    override fun onPlayStarted(layer: InterLayerDisplayPolicyManager.PlayLayer) {
        lock.withLock {
            if(layer.getPushPlayServiceId().isNullOrBlank()) {
                Logger.d(TAG, "[onPlayStarted] keep display layer, PushPlayServiceId is empty.")
                return@withLock
            }

            val existDisplayForPlay = displayLayers.filter {
                it.getDialogRequestId() == layer.getDialogRequestId()
            }.any()

            if(existDisplayForPlay) {
                Logger.d(TAG, "[onPlayStarted] keep display layer, exist display for play.")
                return@withLock
            }

            // maybe sound only layer.

            // clear condition
            displayLayers.filter {
                it.getPushPlayServiceId() != layer.getPushPlayServiceId() && isEvaporatableLayer(it.getLayerType())
            }.forEach {
                Logger.d(TAG, "[onPlayStarted] clear: $it")
                it.clear()
            }

            // refresh condition
            displayLayers.filter {
                it.getPushPlayServiceId() == layer.getPushPlayServiceId()
            }.forEach {
                Logger.d(TAG, "[onPlayStarted] refresh: $it")
                var list = refreshFutureMap[layer]
                if(list == null) {
                    list = ArrayList()
                    refreshFutureMap[layer] = list
                }
                val displayLayer = it
                val future = displayRefreshScheduler.scheduleAtFixedRate({
                    it.refresh()
                }, 0, 900, TimeUnit.MILLISECONDS)
                list.add(Pair(displayLayer, future))
            }

            Logger.d(TAG, "[onPlayStarted] $layer")
        }
    }

    override fun onPlayFinished(layer: InterLayerDisplayPolicyManager.PlayLayer) {
        lock.withLock {
            refreshFutureMap.remove(layer)?.forEach {
                it.second.cancel(true)
                displayRefreshScheduler.submit {
                    it.first.refresh()
                }
            }
            Logger.d(TAG, "[onPlayFinished] $layer")
        }
    }

    override fun addListener(listener: InterLayerDisplayPolicyManager.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: InterLayerDisplayPolicyManager.Listener) {
        listeners.remove(listener)
    }
}