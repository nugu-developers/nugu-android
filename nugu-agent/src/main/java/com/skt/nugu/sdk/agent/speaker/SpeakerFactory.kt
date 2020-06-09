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
package com.skt.nugu.sdk.agent.speaker

/**
 * Provide methods to create speaker which will be used at SDK
 */
interface SpeakerFactory {
    /**
     * Create a speaker for playing nugu such as tts and media.
     */
    @Deprecated("deprecated")
    fun createNuguSpeaker(): Speaker?
    /**
     * Create a speaker for playing alerts
     */
    @Deprecated("deprecated")
    fun createAlarmSpeaker(): Speaker?
    /**
     * Create a speaker for call
     */
    @Deprecated("deprecated")
    fun createCallSpeaker(): Speaker?
    /**
     * Create a speaker for external device
     */
    @Deprecated("deprecated")
    fun createExternalSpeaker(): Speaker?

    fun createSpeaker(type: Speaker.Type): Speaker?
}