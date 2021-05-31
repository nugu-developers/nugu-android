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
package com.skt.nugu.sdk.agent.battery

/**
 * Interface providing battery status.
 */
interface BatteryStatusProvider {
    /**
     * Get the client battery level.
     * @return the battery level which range in (1~100), -1: if unknown
     **/
    fun getBatteryLevel(): Int

    /**
     * Returns whether charging or not.
     * @return true: charging, false: not charging, null: if not supported or unknown.
     */
    fun isCharging(): Boolean?

    /**
     * Returns a level measured as approximate or not
     */
    fun isApproximateLevel(): Boolean? = null
}