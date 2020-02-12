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
package com.skt.nugu.sdk.agent.asr

/** The params for epd timeout
 * @param timeoutInSeconds the silence timeout in seconds
 * @param maxDurationInSeconds the allowed maximum speech duration from SPEECH_START to SPEECH_END in seconds (default = 10sec)
 * @param pauseLengthInMilliseconds the inter-breath time which determine speech end in milliseconds(default = 700ms)
 */
data class EndPointDetectorParam(
    val timeoutInSeconds: Int,
    val maxDurationInSeconds: Int = 10,
    val pauseLengthInMilliseconds: Int = 700
)