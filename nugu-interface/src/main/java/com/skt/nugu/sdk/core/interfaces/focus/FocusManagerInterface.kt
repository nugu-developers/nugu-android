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
package com.skt.nugu.sdk.core.interfaces.focus

import java.util.concurrent.Future

interface FocusManagerInterface {
    data class ChannelConfiguration(
        val name: String,
        val priority: Int,
        val volatile: Boolean = false
    )

    interface OnFocusChangedListener {
        fun onFocusChanged(channelConfiguration: ChannelConfiguration, newFocus: FocusState, interfaceName: String)
    }

    fun acquireChannel(channelName: String, channelObserver: ChannelObserver, interfaceName: String): Boolean
    fun releaseChannel(channelName: String, channelObserver: ChannelObserver): Future<Boolean>

    fun addListener(listener: OnFocusChangedListener)
    fun removeListener(listener: OnFocusChangedListener)
}