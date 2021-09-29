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

/**
 * Manage a display's lifecycle between layers.
 */
interface InterLayerDisplayPolicyManager {
    /**
     * Base interface for Layer
     */
    interface Layer {
        fun getLayerType(): LayerType
        fun getDialogRequestId(): String
        fun getPushPlayServiceId(): String?
    }

    /**
     * The layer for display
     */
    interface DisplayLayer: Layer {
        /**
         * clear the display
         */
        fun clear()

        /**
         * refresh the display's timer
         */
        fun refresh()

        /**
         * the token for display
         */
        val token: String?

        /**
         * the history control for display
         */
        val historyControl: HistoryControl?
    }

    /**
     * The layer for play
     */
    interface PlayLayer: Layer

    /**
     * Should be called when the display rendered
     *
     * @param layer the layer rendered
     */
    fun onDisplayLayerRendered(layer: DisplayLayer)

    /**
     * Should be called when the display cleared
     *
     * @param layer the layer cleared
     */
    fun onDisplayLayerCleared(layer: DisplayLayer)

    /**
     * Should be called when the play started
     *
     * @param layer the layer which play started
     */
    fun onPlayStarted(layer: PlayLayer)

    /**
     * Should be called when the play finished
     *
     * @param layer the layer which play finished
     */
    fun onPlayFinished(layer: PlayLayer)

    /**
     * The listener
     */
    interface Listener {
        /**
         * Called when display layer rendered
         */
        fun onDisplayLayerRendered(layer: DisplayLayer)

        /**
         * Called when display layer cleared
         */
        fun onDisplayLayerCleared(layer: DisplayLayer)
    }

    /**
     * Add listener
     * @param listener the listener
     */
    fun addListener(listener: Listener)

    /**
     * Remove listener
     * @param listener the listener
     */
    fun removeListener(listener: Listener)
}