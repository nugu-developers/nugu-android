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
package com.skt.nugu.sdk.core.interfaces.directive

import java.util.*

/**
 * Defines an BlockingPolicy.
 *
 * @param blockedBy set mediums which blocked
 * @param blocking set mediums which blocking
 */
class BlockingPolicy(
    val blockedBy: EnumSet<Medium>? = null,
    val blocking: EnumSet<Medium>? = null
) {
    enum class Medium {
        AUDIO,
        VISUAL
    }

    companion object {
        val MEDIUM_AUDIO = EnumSet.of(Medium.AUDIO)
    }
}