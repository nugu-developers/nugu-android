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
package com.skt.nugu.sdk.agent.screen

/**
 * Provide a interface for screen
 * * Manipulate screen settings such as brightness and on/off state
 * * Get settings for screen
 */
interface Screen {
    data class Settings(
        /**
         * whether the screen is on or not
         */
        var isOn: Boolean,

        /**
         * the brightness of screen
         */
        var brightness: Long
    )

    /**
     * Turn on the screen with given brightness.
     * @param brightness the brightness
     * @return true: success, false: otherwise
     */
    fun turnOn(brightness: Long): Boolean

    /**
     * Turn off the screen.
     * @return true: success, false: otherwise
     */
    fun turnOff(): Boolean

    /**
     * Set the brightness of screen.
     * @param brightness the brightness
     * @return true: success, false: otherwise
     */
    fun setBrightness(brightness: Long): Boolean


    /**
     * Return a current setting of screen.
     * @return the settings of screen.
     */
    fun getSettings(): Settings
}