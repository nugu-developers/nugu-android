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
package com.skt.nugu.sdk.client

import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.bluetooth.BluetoothAgentInterface
import com.skt.nugu.sdk.agent.delegation.DelegationAgentInterface
import com.skt.nugu.sdk.core.interfaces.connection.NetworkManagerInterface
import com.skt.nugu.sdk.agent.display.DisplayAgentInterface
import com.skt.nugu.sdk.agent.extension.ExtensionAgentInterface
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import com.skt.nugu.sdk.agent.text.TextAgentInterface
import com.skt.nugu.sdk.agent.tts.TTSAgentInterface
import com.skt.nugu.sdk.client.theme.ThemeManagerInterface
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent

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
    val delegationAgent: DelegationAgentInterface?
    val systemAgent: SystemAgentInterface
    val networkManager: NetworkManagerInterface
    val bluetoothAgent: BluetoothAgentInterface?
    val themeManager : ThemeManagerInterface

    fun getAgent(namespace: String): CapabilityAgent?
}