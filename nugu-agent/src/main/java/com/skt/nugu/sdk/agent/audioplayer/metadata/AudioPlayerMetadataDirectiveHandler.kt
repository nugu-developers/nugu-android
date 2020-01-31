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
package com.skt.nugu.sdk.agent.audioplayer.metadata

import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.audioplayer.AbstractAudioPlayerAgent
import com.skt.nugu.sdk.agent.display.AudioPlayerTemplateHandler
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import java.util.concurrent.CopyOnWriteArraySet

class AudioPlayerMetadataDirectiveHandler: AbstractDirectiveHandler() {
    companion object {
        const val NAMESPACE =
            AbstractAudioPlayerAgent.NAMESPACE
        const val VERSION =
            AbstractAudioPlayerAgent.VERSION

        // v1.1
        private const val NAME_UPDATE_METADATA = "UpdateMetadata"

        private val UPDATE_METADATA =
            NamespaceAndName(
                AudioPlayerTemplateHandler.NAMESPACE,
                NAME_UPDATE_METADATA
            )
    }

    interface Listener {
        fun onMetadataUpdate(playServiceId: String, jsonMetaData: String)
    }

    private data class UpdateMetadataPayload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("metadata")
        val metadata: String
    )

    private val supportConfigurations = HashMap<NamespaceAndName, BlockingPolicy>()

    private val listeners = CopyOnWriteArraySet<Listener>()

    init {
        supportConfigurations[UPDATE_METADATA] = BlockingPolicy()
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // nothing to do
    }

    override fun handleDirective(info: DirectiveInfo) {
        info.result.setCompleted()

        with(info.directive) {
            val updateMetadataPayload = MessageFactory.create(payload, UpdateMetadataPayload::class.java)
                ?: return

            if (getNamespaceAndName() == UPDATE_METADATA) {
                listeners.forEach {listener ->
                    listener.onMetadataUpdate(updateMetadataPayload.playServiceId, updateMetadataPayload.metadata)
                }
            }
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> = supportConfigurations

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}