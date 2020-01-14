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
package com.skt.nugu.sdk.core.agent.audioplayer

import com.google.gson.JsonObject
import com.skt.nugu.sdk.core.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.core.agent.display.DisplayAudioPlayerAgent
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.core.directivesequencer.DirectiveGroupPreprocessor
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.util.MessageFactory
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration

class AudioPlayerDirectivePreProcessor :
    DirectiveGroupPreprocessor {
    companion object {
        private const val TAG = "AudioPlayerDirectivePreProcessor"
    }

    override fun preprocess(directives: List<Directive>): MutableList<Directive> {
        val processedDirectives = ArrayList(directives)
        val audioPlayerPlayDirective =
            processedDirectives.find { it.getNamespaceAndName() == AbstractAudioPlayerAgent.PLAY }

        if (audioPlayerPlayDirective == null) {
            return processedDirectives
        }

        val displayDirective =
            directives.find { it.getNamespace() == "Display" }

        if (displayDirective != null) {
            Logger.d(TAG, "[preprocess] do not display audio player template display")
            return processedDirectives
        }

        val playPayload = MessageFactory.create(audioPlayerPlayDirective.payload, DefaultAudioPlayerAgent.PlayPayload::class.java)
        if (playPayload == null) {
            Logger.d(TAG, "[preprocess] no payload for audio player play")
            return processedDirectives
        }

        val audioItem = playPayload.audioItem
        if (audioItem == null) {
            Logger.d(TAG, "[preprocess] no audio item.")
            return processedDirectives
        }

        val metaData = audioItem.metaData
        if (metaData == null) {
            Logger.d(TAG, "[preprocess] no metaData.")
            return processedDirectives
        }

        if (metaData.disableTemplate != null && metaData.disableTemplate) {
            Logger.d(TAG, "[preprocess] metaData template disabled.")
            return processedDirectives
        }

        if(metaData.template == null) {
            Logger.d(TAG, "[preprocess] metaData template is null.")
            return processedDirectives
        }

        val audioDisplayDirective = createAudioDisplayDirective(metaData.template, audioPlayerPlayDirective.getDialogRequestId(), playPayload)

        if (audioDisplayDirective == null) {
            Logger.d(TAG, "[preprocess] metaData template is null.")
            return processedDirectives
        }

        Logger.d(TAG, "[preprocess] create audio player template directive to display")
        processedDirectives.add(0, audioDisplayDirective)

        return processedDirectives
    }

    private fun createAudioDisplayDirective(
        template: JsonObject,
        dialogRequestId: String,
        playPayload: DefaultAudioPlayerAgent.PlayPayload
    ): Directive? {
        return try {
            template.addProperty("playServiceId", playPayload.playServiceId)

            val type = template.getAsJsonPrimitive("type").asString.split(".")
            val namespace = type[0]
            val name = type[1]

            playPayload.playStackControl?.let {
                template.add("playStackControl", it.toJsonObject())
            }

            MessageFactory.createDirective(
                null, Header(
                    dialogRequestId,
                    UUIDGeneration.shortUUID().toString(),
                    name,
                    namespace,
                    DisplayAudioPlayerAgent.VERSION
                ), template
            )
        } catch (th: Throwable) {
            Logger.w(TAG, "[createAudioDisplayDirective] failed to create", th)
            null
        }
    }
}