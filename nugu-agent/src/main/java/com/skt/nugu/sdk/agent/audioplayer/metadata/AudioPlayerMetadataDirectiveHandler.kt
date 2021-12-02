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

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.display.AudioPlayerTemplateHandler
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import java.util.concurrent.CopyOnWriteArraySet

class AudioPlayerMetadataDirectiveHandler: AbstractDirectiveHandler() {
    companion object {
        const val NAMESPACE =
            DefaultAudioPlayerAgent.NAMESPACE
        val VERSION =
            DefaultAudioPlayerAgent.VERSION

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
        val metadata: JsonObject
    )

    private val listeners = CopyOnWriteArraySet<Listener>()

    override fun preHandleDirective(info: DirectiveInfo) {
        // nothing to do
    }

    override fun handleDirective(info: DirectiveInfo) {
        with(info.directive) {
            val updateMetadataPayload = MessageFactory.create(payload, UpdateMetadataPayload::class.java)
            if(updateMetadataPayload == null) {
                info.result.setFailed("[handleDirective] invalid payload: $updateMetadataPayload")
                return
            }

            info.result.setCompleted()
            if (getNamespaceAndName() == UPDATE_METADATA) {
                listeners.forEach {listener ->
                    listener.onMetadataUpdate(updateMetadataPayload.playServiceId, updateMetadataPayload.metadata.toString())
                }
            }
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[UPDATE_METADATA] = BlockingPolicy.sharedInstanceFactory.get()
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}