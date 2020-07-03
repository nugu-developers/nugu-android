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
 * Provide a interface for speaker
 * * Manipulate speaker settings such as volume and (un)muted.
 * * Get settings and properties(min/max)
 */
interface Speaker {
    enum class Type {
        NUGU,
        MUSIC,
        ALARM,
        RINGTONE,
        CALL,
        NOTIFICATION,
        VOICE_COMMAND,
        NAVIGATION,
        SYSTEM_SOUND
    }

    enum class Rate {
        SLOW, FAST
    }

    data class SpeakerSettings(
        /**
         * current volume which range in ([getMinVolume] .. [getMaxVolume])
         */
        var volume: Int?,
        /**
         * current mute state, true: muted, false: unmuted
         */
        var mute: Boolean?
    )

    /** Get the type of the speaker.
     * Must be static value.
     * @return [Type]
     */
    fun getSpeakerType(): Type

    /**
     * Get the group of the speaker
     */
    fun getGroup(): String? = null

    /**
     * Set the volume of the speaker.
     *
     * @param volume amount of volume range in ([getMinVolume] .. [getMaxVolume])
     * @return success or not
     */
    fun setVolume(volume: Int, rate: Rate = Rate.FAST): Boolean

    /**
     * Set the mute of the speaker.
     *
     * @param mute true: muted, false: unmuted
     * @return success or not
     */
    fun setMute(mute: Boolean): Boolean

    @Deprecated("revise")
    fun adjustVolume(deltaVolume: Int): Boolean

    /**
     * Get the current settings of the speaker
     *
     * @return [SpeakerSettings] object. If failed, null.
     */
    fun getSpeakerSettings(): SpeakerSettings?

    /**
     * Get the maximum volume of the speaker.
     *
     * Must be static value.
     *
     * @return the maximum volume, null if not supported
     */
    fun getMaxVolume(): Int?

    /**
     * Get the minimum volume of the speaker.
     *
     * Must be static value.
     *
     * @return the minimum volume, null if not supported
     */
    fun getMinVolume(): Int?

    /**
     * Get the default volume step of the speaker used when volumeUp/Down.
     *
     * Must be static value.
     *
     * @return the volume step, null if not supported
     */
    fun getDefaultVolumeStep(): Int?

    /**
     * Get the default volume level of the speaker
     *
     * Must be static value.
     *
     * @return the volume level, null if not supported
     */
    fun getDefaultVolumeLevel(): Int?
}