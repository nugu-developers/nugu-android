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
package com.skt.nugu.sdk.core.directivesequencer

import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.core.interfaces.capability.display.AbstractDisplayAgent
import com.skt.nugu.sdk.core.capabilityagents.impl.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.message.MessageFactory
import com.skt.nugu.sdk.core.utils.Logger

class AudioPlayerDirectivePreProcessor : DirectiveGroupPreprocessor {
    companion object {
        private const val TAG = "AudioPlayerDirectivePreProcessor"
    }

    override fun preprocess(directives: ArrayList<Directive>): ArrayList<Directive> {
        val audioPlayerPlayDirective =
            directives.find { it.getNamespaceAndName() == AbstractAudioPlayerAgent.PLAY }

        if (audioPlayerPlayDirective == null) {
            return directives
        }

        val displayDirective =
            directives.find { it.getNamespace() == "Display" }

        if (displayDirective != null) {
            Logger.d(TAG, "[preprocess] do not display audio player template display")
            return directives
        }

        val playPayload = MessageFactory.create(audioPlayerPlayDirective.payload, DefaultAudioPlayerAgent.PlayPayload::class.java)
        if (playPayload == null) {
            Logger.d(TAG, "[preprocess] no payload for audio player play")
            return directives
        }

        val audioItem = playPayload.audioItem
        if (audioItem == null) {
            Logger.d(TAG, "[preprocess] no audio item.")
            return directives
        }

        val metaData = audioItem.metaData
        if (metaData == null) {
            Logger.d(TAG, "[preprocess] no metaData.")
            return directives
        }

        if (metaData.disableTemplate != null && metaData.disableTemplate) {
            Logger.d(TAG, "[preprocess] metaData template disabled.")
            return directives
        }

        val metaDataTemplate = metaData.asDisplayDirective(
            audioPlayerPlayDirective.getDialogRequestId(),
            playPayload.playServiceId
        )

        if (metaDataTemplate == null) {
            Logger.d(TAG, "[preprocess] metaData template is null.")
            return directives
        }

        Logger.d(TAG, "[preprocess] create audio player template directive to display")
        directives.add(0, metaDataTemplate)

        return directives
    }
}