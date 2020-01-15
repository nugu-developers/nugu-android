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
import com.skt.nugu.sdk.agent.system.BatteryStatusProvider

/**
 * Default implementation of [BatteryStatusProvider] for Android Battery status.
 */
class AndroidBatteryStatusProvider(
    private val context: Context
) : BatteryStatusProvider{

    override fun getBatteryLevel(): Int {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.apply {
            return getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        }
        return -1
    }
}