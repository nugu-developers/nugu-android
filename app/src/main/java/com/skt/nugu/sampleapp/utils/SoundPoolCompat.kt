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
package com.skt.nugu.sampleapp.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import com.skt.nugu.sampleapp.R

object SoundPoolCompat {
    private var soundPool: SoundPool? = null
    private val localBeepIds = ConcurrentHashMap<LocalBeep, Int>()
    private val localTTSIds = ConcurrentHashMap<LocalTTS, Int>()

    /**
     * The enum LocalBeep describes the state of asr.
     **/
    enum class LocalBeep constructor(resId: Int) {
        FAIL(R.raw.responsefail_800ms),
        SUCCESS(R.raw.responsesuccess_800ms),
        WAKEUP(R.raw.wakeup_500ms);

        var resourceId: Int = 0
            internal set

        init {
            this.resourceId = resId
        }
    }

    /**
     * The enum LocalTTS describes the state of error.
     * @see [https://developers-doc.nugu.co.kr/nugu-sdk/sdk-design-guide/error-handling]
     **/
    enum class LocalTTS constructor(resId: Int) {
        DEVICE_GATEWAY_NETWORK_ERROR(R.raw.device_gw_error_001),
        DEVICE_GATEWAY_SERVER_ERROR_TRY_AGAIN(R.raw.device_gw_error_002),
        DEVICE_GATEWAY_UNAUTHORIZED_ERROR(R.raw.device_gw_error_003),
        DEVICE_GATEWAY_REQUEST_TIMEOUT_ERROR(R.raw.device_gw_error_004),
        DEVICE_GATEWAY_NOTACCEPTABLE_ERROR(R.raw.device_gw_error_005),
        DEVICE_GATEWAY_TTS_ERROR(R.raw.device_gw_error_006),
        DEVICE_GATEWAY_PLAY_ROUTER_ERROR(R.raw.device_gw_error_006);

        var resourceId: Int = 0
            internal set

        init {
            this.resourceId = resId
        }
    }

    /**
     * Load the resource from the specified raw files.
     */
    fun load(context: Context) {
        release()

        soundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .build()
            SoundPool.Builder().setMaxStreams(1).setAudioAttributes(audioAttributes).build()
        } else {
            SoundPool(1, AudioManager.STREAM_MUSIC, 0)
        }

        LocalBeep.values().forEach {
            soundPool?.let { pool ->
                localBeepIds[it] = pool.load(context, it.resourceId, 1)
            }
        }
        LocalTTS.values().forEach {
            soundPool?.let { pool ->
                localTTSIds[it] = pool.load(context, it.resourceId, 1)
            }
        }
    }

    /**
     * Play a beep from a sound ID.
     */
    fun play(beep: LocalBeep) {
        localBeepIds[beep]?.let {
            soundPool?.play(it, 1f, 1f, 0, 0, 1f)
        }
    }

    /**
     * Play a tts from a sound ID.
     */
    fun play(tts: LocalTTS) {
        localTTSIds[tts]?.let {
            soundPool?.play(it, 1f, 1f, 0, 0, 1f)
        }
    }

    /**
     * Release the resources.
     */
    fun release() {
        Log.d("SoundPoolCompat", "release")
        localBeepIds.clear()
        localTTSIds.clear()
        soundPool?.release()
    }
}