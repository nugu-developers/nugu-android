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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class InterLayerDisplayPolicyManagerImpl: InterLayerDisplayPolicyManager {
    companion object {
        private const val TAG = "InterLayerDisplayPolicyManager"
    }

    private val lock = ReentrantLock()
    private val displayLayers = HashSet<InterLayerDisplayPolicyManager.DisplayLayer>()

    override fun onDisplayLayerRendered(layer: InterLayerDisplayPolicyManager.DisplayLayer) {
        lock.withLock {
            if(displayLayers.contains(layer)) {
                Logger.d(TAG, "[onDisplayLayerRendered] already called: $layer")
                return@withLock
            }

            if(layer.getLayerType() == LayerType.INFO) {
                displayLayers.filter {
                    when(it.getLayerType()) {
                        LayerType.MEDIA,
                        LayerType.CALL,
                        LayerType.NAVI -> false
                        else -> !it.getPlayServiceId()
                            .isNullOrBlank() && it.getPlayServiceId() != layer.getPlayServiceId()
                    }
                }.forEach {
                    it.clear()
                }
            }

            displayLayers.add(layer)
            Logger.d(TAG, "[onDisplayLayerRendered] rendered: $layer")
        }
    }

    override fun onDisplayLayerCleared(layer: InterLayerDisplayPolicyManager.DisplayLayer) {
        lock.withLock {
            val removed = displayLayers.remove(layer)
            Logger.d(TAG, "[onDisplayLayerCleared] clear: $layer, removed: $removed")
        }
    }

    override fun onPlayStarted(layer: InterLayerDisplayPolicyManager.PlayLayer) {
        lock.withLock {
            if(layer.getPushPlayServiceId().isNullOrBlank()) {
                Logger.d(TAG, "[onPlayStarted] keep display layer, PushPlayServiceId is empty.")
                return@withLock
            }

            if(layer.getPlayServiceId().isNullOrBlank()) {
                Logger.d(TAG, "[onPlayStarted] keep display layer, PlayServiceId is empty.")
                return@withLock
            }

            val existDisplayForPlay = displayLayers.filter {
                it.getDialogRequestId() == layer.getDialogRequestId()
            }.any()

            if(existDisplayForPlay) {
                Logger.d(TAG, "[onPlayStarted] keep display layer, exist display for play.")
                return@withLock
            }

            displayLayers.filter {
                when (it.getLayerType()) {
                    LayerType.INFO,
                    LayerType.ALERT,
                    LayerType.OVERLAY_DISPLAY -> true
                    else -> false
                }
            }.forEach {
                it.clear()
            }
            Logger.d(TAG, "[onPlayStarted] $layer")
        }
    }
}