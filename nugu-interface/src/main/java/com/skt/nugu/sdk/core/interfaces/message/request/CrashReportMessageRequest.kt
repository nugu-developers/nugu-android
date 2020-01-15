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
package com.skt.nugu.sdk.core.interfaces.message.request

import com.skt.nugu.sdk.core.interfaces.message.MessageRequest

/**
 * Class for requesting a crashReport message
 * @param level is level of report
 * @param message is description of report
 */
open class CrashReportMessageRequest(
    val level: Level,
    val message: String
): MessageRequest {
    /**
     * Level that can be used
     * @param value is Level
     */
    enum class Level(val value: Int) {
        /**
         * error level
         */
        ERROR(0),
        /**
         * warn level
         */
        WARN(1),
        /**
         * info level
         */
        INFO(2),
        /**
         * debug level
         */
        DEBUG(3),
        /**
         * trace level
         */
        TRACE(4)
    }
}