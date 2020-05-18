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

package com.skt.nugu.sdk.agent.ext.navigation.payload

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.ext.navigation.Poi

data class SendPoiCandidatesPayload(
    @SerializedName("playServiceId")
    val playServiceId: String,
    @SerializedName("poiCandidates")
    val poiCandidates: Array<Poi>?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SendPoiCandidatesPayload

        if (playServiceId != other.playServiceId) return false
        if (poiCandidates != null) {
            if (other.poiCandidates == null) return false
            if (!poiCandidates.contentEquals(other.poiCandidates)) return false
        } else if (other.poiCandidates != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = playServiceId.hashCode()
        result = 31 * result + (poiCandidates?.contentHashCode() ?: 0)
        return result
    }
}