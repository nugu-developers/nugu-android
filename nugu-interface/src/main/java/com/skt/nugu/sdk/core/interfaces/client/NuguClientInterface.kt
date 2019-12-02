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
package com.skt.nugu.sdk.core.interfaces.client

import com.skt.nugu.sdk.core.interfaces.capability.asr.ASRAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.delegation.DelegationAgentInterface
import com.skt.nugu.sdk.core.interfaces.connection.NetworkManagerInterface
import com.skt.nugu.sdk.core.interfaces.capability.display.DisplayAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.extension.ExtensionAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.location.LocationAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.system.SystemAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.text.TextAgentInterface
import com.skt.nugu.sdk.core.interfaces.capability.tts.TTSAgentInterface

/**
 * This is an utility interface which is exposed to application that interact with NUGU
 */
interface NuguClientInterface {
    val audioPlayerAgent: AudioPlayerAgentInterface?
    val ttsAgent: TTSAgentInterface?
    val displayAgent: DisplayAgentInterface?
    val extensionAgent: ExtensionAgentInterface?
    val asrAgent: ASRAgentInterface?
    val textAgent: TextAgentInterface?
    val locationAgent: LocationAgentInterface?
    val delegationAgent: DelegationAgentInterface?
    val systemAgent: SystemAgentInterface
    val networkManager: NetworkManagerInterface
}