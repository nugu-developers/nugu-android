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

package com.skt.nugu.sdk.agent.ext.mediaplayer.payload

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.ext.mediaplayer.Action
import com.skt.nugu.sdk.agent.ext.mediaplayer.Target

data class PreviousPayload(
    @SerializedName("playServiceId")
    val playServiceId: String,
    /**
     * the unique string to identify
     */
    @SerializedName("token")
    val token: String,
    @SerializedName("action")
    val action: Action,
    @SerializedName("target")
    val target: Target,
    @SerializedName("data")
    val data: JsonObject?
)