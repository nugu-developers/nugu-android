/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.platform.android.service.webkit

import androidx.annotation.VisibleForTesting

object JavaScriptHelper {
    @VisibleForTesting
    const val JAVASCRIPT_ON_ROUTINE_STATUS_CHANGED = "javascript:onRoutineStatusChanged('%s', '%s');"
    fun formatOnRoutineStatusChanged(token: String, status: String): String {
        return JAVASCRIPT_ON_ROUTINE_STATUS_CHANGED.format(token, status)
    }
}