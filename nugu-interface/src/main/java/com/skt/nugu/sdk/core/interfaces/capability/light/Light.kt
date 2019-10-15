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
package com.skt.nugu.sdk.core.interfaces.capability.light

/**
 * Provide a interface for light
 * * Manipulate light settings such as on/off, mode and brightness.
 * * Get settings and properties
 */
interface Light {
    enum class Mode {
        COLOR_WHITE,
        COLOR_PINK,
        COLOR_ORANGE,
        COLOR_YELLOW,
        COLOR_BLUE,
        COLOR_PURPLE,
        COLOR_RAINBOW,
        COLOR_BONFINE,
        COLOR_AURORA,
        COLOR_JOYFUL,
        COLOR_COZY,
        COLOR_CALM,
        COLOR_SWEET,
        COLOR_PRETTY,
        COLOR_CORAL,
        COLOR_LIME,
        COLOR_SKY,
        COLOR_WINE,
        COLOR_NATURAL_WHITE,
        COLOR_WARM_WHITE,
        COLOR_PARTY_LIGHT,

        MODE_NURSING,
        MODE_READING,
        MODE_SLEEP,
        MODE_SUNRINSE
    }

    data class LightSettings(
        /**
         * the current on/off state of light
         */
        var isOn: Boolean,
        /**
         * the current mode of light which contained at [getSupportedModes]
         */
        var mode: Mode,
        /**
         * the current brightness which range in ([getMinBrightness] .. [getMaxBrightness])
         * * If off, default brightness.
         * * If not supported, null allowed.
         */
        var brightness: Long?
    )


    /**
     * turn on the light with [mode] and [brightness]
     *
     * @param mode the mode to be set
     * @param brightness the brightness to be set
     * @return true: on, otherwise: false
     */
    fun turnOnLight(mode: Mode, brightness: Long?): Boolean

    /**
     * turn off the light
     *
     * @return true: off, otherwise: false
     */
    fun turnOffLight(): Boolean

    /**
     * change setting of the light with [mode] and [brightness]
     *
     * @param mode the mode to be set
     * @param brightness the brightness to be set
     * @return true: on, otherwise: false
     */
    fun changeLight(mode: Mode, brightness: Long?): Boolean
    fun flicker(onTimeInMilliseconds: Long, offTimeInMilliseconds: Long, repeatCount: Long, mode: Mode): Boolean

    /**
     * Get the current settings of the light
     *
     * @return [LightSettings] object. If failed, null.
     */
    fun getLightSettings(): LightSettings?

    /**
     * Get the maximum brightness of the light.
     *
     * Must be static value.
     *
     * @return the maximum brightness if supported, otherwise null
     */
    fun getMaxBrightness(): Long?

    /**
     * Get the minimum brightness of the light.
     *
     * Must be static value.
     *
     * @return the minimum brightness if supported, otherwise null
     */
    fun getMinBrightness(): Long?

    /**
     * Get the supported modes by the light.
     *
     * Even if the light only allow on/off, should be support at least one mode.
     *
     * Do not return empty array.
     *
     * Must be static value.
     *
     * @return the supported modes.
     */
    fun getSupportedModes(): Array<Mode>

    /**
     * Get the maximum flicker count.
     *
     * Must be static value.
     *
     * @return the maximum flicker count if supported, otherwise null
     */
    fun getMaxFlickerCount(): Long?
}