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
package com.skt.nugu.sdk.agent.asr

import com.google.gson.annotations.SerializedName

data class AsrNotifyResultPayload(
    @SerializedName("state")
    val state: State,
    @SerializedName("result")
    val result: String?
) {
    enum class State {
        @SerializedName("PARTIAL")
        PARTIAL,
        @SerializedName("COMPLETE")
        COMPLETE,
        @SerializedName("NONE")
        NONE,
        @SerializedName("SOS")
        SOS,
        @SerializedName("EOS")
        EOS,
        @SerializedName("FA")
        FA,
        @SerializedName("ERROR")
        ERROR
    }

    fun isValidPayload(): Boolean = !((state == State.PARTIAL || state == State.COMPLETE) && result.isNullOrBlank())
}