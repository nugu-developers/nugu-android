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

package com.skt.nugu.sdk.core.interfaces.display

interface InterLayerDisplayPolicyManager {
    interface Layer {
        fun getLayerType(): LayerType
        fun getDialogRequestId(): String
        fun getPushPlayServiceId(): String?
    }

    interface DisplayLayer: Layer {
        fun clear()
        fun refresh()
    }

    interface PlayLayer: Layer

    fun onDisplayLayerRendered(layer: DisplayLayer)
    fun onDisplayLayerCleared(layer: DisplayLayer)
    fun onPlayStarted(layer: PlayLayer)
    fun onPlayFinished(layer: PlayLayer)

    interface Listener {
        fun onDisplayLayerRendered(layer: DisplayLayer)
        fun onDisplayLayerCleared(layer: DisplayLayer)
    }

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)
}