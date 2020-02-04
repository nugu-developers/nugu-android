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
package com.skt.nugu.sdk.platform.android.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.agent.battery.BatteryStatusProvider

/**
 * Default implementation of [BatteryStatusProvider] for Android Battery status.
 */
class AndroidBatteryStatusProvider(
    private val context: Context
) : BatteryStatusProvider {
    companion object {
        private const val TAG = "AndroidBatteryStatusProvider"
        private const val REFRESH_INTERVAL = 10L
    }

    private var lastUsedBatteryStatusIntentCreated: Long = -1L
    private var lastUsedBatteryStatusIntent: Intent? = null

    override fun getBatteryLevel(): Int {
        val level = getBatteryStatusIntent()?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        Logger.d(TAG, "[getBatteryLevel] level: $level")
        return level
    }

    override fun isCharging(): Boolean? {
        var pluggedStatus: Int? = null
        val charging = getBatteryStatusIntent()?.let {
            pluggedStatus = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            when (pluggedStatus) {
                BatteryManager.BATTERY_PLUGGED_AC,
                BatteryManager.BATTERY_PLUGGED_USB,
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> true
                else -> false
            }
        }
        Logger.d(TAG, "[isCharging] charging: $charging / pluggedStatus: $pluggedStatus")
        return charging
    }

    private fun getBatteryStatusIntent(): Intent? {
        val currentElapsed = SystemClock.elapsedRealtime()
        var intent = lastUsedBatteryStatusIntent

        if(intent == null || currentElapsed - lastUsedBatteryStatusIntentCreated > REFRESH_INTERVAL) {
            intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            lastUsedBatteryStatusIntent = intent
            lastUsedBatteryStatusIntentCreated = currentElapsed
            Logger.d(TAG, "[getBatteryStatusIntent] battery status refreshed")
        }

        return intent
    }
}