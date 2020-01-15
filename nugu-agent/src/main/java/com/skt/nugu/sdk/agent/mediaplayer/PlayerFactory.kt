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
package com.skt.nugu.sdk.agent.mediaplayer

/**
 * Provide methods to create player which will be used at SDK
 */
interface PlayerFactory {
    /**
     * Create a player for playing tts
     */
    fun createSpeakPlayer(): MediaPlayerInterface

    /**
     * Create a player for playing audio
     */
    fun createAudioPlayer(): MediaPlayerInterface

    /**
     * Create a player for playing alerts
     */
    fun createAlertsPlayer(): MediaPlayerInterface

    /**
     * Create a player to control Bluetooth
     */
//    fun createBluetoothPlayer(): MediaPlayerInterface

    /**
     * Create a player for playing ringtone
     */
//    fun createRingtonePlayer(): MediaPlayerInterface

    /**
     * Create a player for playing beep
     */
    fun createBeepPlayer(): UriSourcePlayablePlayer

}