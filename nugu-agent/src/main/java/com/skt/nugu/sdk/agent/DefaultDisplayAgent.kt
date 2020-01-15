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
package com.skt.nugu.sdk.agent

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.display.BaseDisplayAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.inputprocessor.InputProcessorManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.utils.Logger
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap

class DefaultDisplayAgent(
    focusManager: FocusManagerInterface,
    contextManager: ContextManagerInterface,
    messageSender: MessageSender,
    playSynchronizer: PlaySynchronizerInterface,
    playStackManager: PlayStackManagerInterface,
    inputProcessorManager: InputProcessorManagerInterface,
    channelName: String
) : BaseDisplayAgent(
    focusManager,
    contextManager,
    messageSender,
    playSynchronizer,
    playStackManager,
    inputProcessorManager,
    channelName
) {
    companion object {
        private const val TAG = "DisplayTemplateAgent"

        const val NAMESPACE = "Display"
        const val VERSION = "1.1"

        // supported types for v1.0
        private const val NAME_FULLTEXT1 = "FullText1"
        private const val NAME_FULLTEXT2 = "FullText2"
        private const val NAME_IMAGETEXT1 = "ImageText1"
        private const val NAME_IMAGETEXT2 = "ImageText2"
        private const val NAME_IMAGETEXT3 = "ImageText3"
        private const val NAME_IMAGETEXT4 = "ImageText4"
        private const val NAME_TEXTLIST1 = "TextList1"
        private const val NAME_TEXTLIST2 = "TextList2"
        private const val NAME_TEXTLIST3 = "TextList3"
        private const val NAME_TEXTLIST4 = "TextList4"
        private const val NAME_IMAGELIST1 = "ImageList1"
        private const val NAME_IMAGELIST2 = "ImageList2"
        private const val NAME_IMAGELIST3 = "ImageList3"
        private const val NAME_CUSTOMTEMPLATE = "CustomTemplate"

        // supported types for v1.1
        private const val NAME_WEATHER_1 = "Weather1"
        private const val NAME_WEATHER_2 = "Weather2"
        private const val NAME_WEATHER_3 = "Weather3"
        private const val NAME_WEATHER_4 = "Weather4"
        private const val NAME_WEATHER_5 = "Weather5"
        private const val NAME_FULLIMAGE = "FullImage"
        private const val NAME_CLOSE = "Close"
        private const val NAME_CLOSE_SUCCEEDED = "CloseSucceeded"
        private const val NAME_CLOSE_FAILED = "CloseFailed"

        private val FULLTEXT1 = NamespaceAndName(
            NAMESPACE,
            NAME_FULLTEXT1
        )
        private val FULLTEXT2 = NamespaceAndName(
            NAMESPACE,
            NAME_FULLTEXT2
        )
        private val IMAGETEXT1 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGETEXT1
        )
        private val IMAGETEXT2 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGETEXT2
        )
        private val IMAGETEXT3 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGETEXT3
        )
        private val IMAGETEXT4 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGETEXT4
        )
        private val TEXTLIST1 = NamespaceAndName(
            NAMESPACE,
            NAME_TEXTLIST1
        )
        private val TEXTLIST2 = NamespaceAndName(
            NAMESPACE,
            NAME_TEXTLIST2
        )

        private val TEXTLIST3 = NamespaceAndName(
            NAMESPACE,
            NAME_TEXTLIST3
        )

        private val TEXTLIST4 = NamespaceAndName(
            NAMESPACE,
            NAME_TEXTLIST4
        )

        private val IMAGELIST1 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGELIST1
        )
        private val IMAGELIST2 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGELIST2
        )
        private val IMAGELIST3 = NamespaceAndName(
            NAMESPACE,
            NAME_IMAGELIST3
        )
        private val CUSTOM_TEMPLATE = NamespaceAndName(
            NAMESPACE,
            NAME_CUSTOMTEMPLATE
        )

        private val WEATHER1 = NamespaceAndName(
            NAMESPACE,
            NAME_WEATHER_1
        )

        private val WEATHER2 = NamespaceAndName(
            NAMESPACE,
            NAME_WEATHER_2
        )

        private val WEATHER3 = NamespaceAndName(
            NAMESPACE,
            NAME_WEATHER_3
        )

        private val WEATHER4 = NamespaceAndName(
            NAMESPACE,
            NAME_WEATHER_4
        )

        private val WEATHER5 = NamespaceAndName(
            NAMESPACE,
            NAME_WEATHER_5
        )

        private val FULLIMAGE = NamespaceAndName(
            NAMESPACE,
            NAME_FULLIMAGE
        )

        private val CLOSE = NamespaceAndName(
            NAMESPACE,
            NAME_CLOSE
        )

        private val CLOSE_SUCCEEDED = NamespaceAndName(
            NAMESPACE,
            NAME_CLOSE_SUCCEEDED
        )

        private val CLOSE_FAILED = NamespaceAndName(
            NAMESPACE,
            NAME_CLOSE_FAILED
        )
    }

    private data class ClosePayload(
        @SerializedName("playServiceId")
        val playServiceId: String
    )

    init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun getNamespace(): String =
        NAMESPACE

    override fun getVersion(): String =
        VERSION

    override fun getContextPriority(): Int = 100

    override fun preHandleDirective(info: DirectiveInfo) {
        if (info.directive.getNamespaceAndName() != CLOSE) {
            super.preHandleDirective(info)
        }
    }

    override fun handleDirective(info: DirectiveInfo) {
        if (info.directive.getNamespaceAndName() != CLOSE) {
            super.handleDirective(info)
        } else {
            executor.submit {
                val closePayload =
                    MessageFactory.create(info.directive.payload, ClosePayload::class.java)
                if (closePayload == null) {
                    Logger.w(TAG, "[handleDirective] (Close) no playServiceId at Payload.")
                    sendCloseFailed(info, "")
                    return@submit
                }

                val currentRenderedInfo = currentInfo ?: return@submit
                val currentPlayServiceId = currentRenderedInfo.payload.playServiceId
                if (currentPlayServiceId.isNullOrBlank()) {
                    Logger.w(
                        TAG,
                        "[handleDirective] (Close) no current info (maybe already closed)."
                    )
                    sendCloseSucceeded(info, closePayload.playServiceId)
                    return@submit
                }

                Logger.w(
                    TAG,
                    "[handleDirective] (Close) current : $currentPlayServiceId / close : ${closePayload.playServiceId}"
                )
                if (currentPlayServiceId == closePayload.playServiceId) {
                    executeCancelUnknownInfo(currentRenderedInfo, true)
                    sendCloseEventWhenClosed(currentRenderedInfo, info)
                } else {
                    sendCloseSucceeded(info, closePayload.playServiceId)
                }
            }
        }
    }

    override fun onDisplayCardCleared(templateDirectiveInfo: TemplateDirectiveInfo) {
        val pendingCloseSucceededEvent = pendingCloseSucceededEvents[templateDirectiveInfo]
            ?: return

        val payload = MessageFactory.create(
            pendingCloseSucceededEvent.directive.payload,
            ClosePayload::class.java
        ) ?: return

        sendCloseSucceeded(pendingCloseSucceededEvent, payload.playServiceId)
    }

    private val pendingCloseSucceededEvents =
        ConcurrentHashMap<TemplateDirectiveInfo, DirectiveInfo>()

    private fun sendCloseEventWhenClosed(
        closeTargetTemplateDirective: TemplateDirectiveInfo,
        closeDirective: DirectiveInfo
    ) {
        pendingCloseSucceededEvents[closeTargetTemplateDirective] = closeDirective
    }

    private fun sendCloseEvent(eventName: String, info: DirectiveInfo, playServiceId: String) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                messageSender.sendMessage(
                    EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        eventName,
                        VERSION
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", playServiceId)
                    }.toString()).referrerDialogRequestId(info.directive.getDialogRequestId()).build()
                )
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {
            }
        }, namespaceAndName)
    }

    private fun sendCloseSucceeded(info: DirectiveInfo, playServiceId: String) {
        sendCloseEvent(NAME_CLOSE_SUCCEEDED, info, playServiceId)
    }

    private fun sendCloseFailed(info: DirectiveInfo, playServiceId: String) {
        sendCloseEvent(NAME_CLOSE_FAILED, info, playServiceId)
    }

    override fun executeOnFocusBackground(info: DirectiveInfo) {
        getRenderer()?.clear(info.directive.getMessageId(), true)
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[FULLTEXT1] = nonBlockingPolicy
        configuration[FULLTEXT2] = nonBlockingPolicy
        configuration[IMAGETEXT1] = nonBlockingPolicy
        configuration[IMAGETEXT2] = nonBlockingPolicy
        configuration[IMAGETEXT3] = nonBlockingPolicy
        configuration[IMAGETEXT4] = nonBlockingPolicy
        configuration[TEXTLIST1] = nonBlockingPolicy
        configuration[TEXTLIST2] = nonBlockingPolicy
        configuration[TEXTLIST3] = nonBlockingPolicy
        configuration[TEXTLIST4] = nonBlockingPolicy
        configuration[IMAGELIST1] = nonBlockingPolicy
        configuration[IMAGELIST2] = nonBlockingPolicy
        configuration[IMAGELIST3] = nonBlockingPolicy
        configuration[CUSTOM_TEMPLATE] = nonBlockingPolicy

        configuration[WEATHER1] = nonBlockingPolicy
        configuration[WEATHER2] = nonBlockingPolicy
        configuration[WEATHER3] = nonBlockingPolicy
        configuration[WEATHER4] = nonBlockingPolicy
        configuration[WEATHER5] = nonBlockingPolicy
        configuration[FULLIMAGE] = nonBlockingPolicy

        configuration[CLOSE] = nonBlockingPolicy

        return configuration
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            contextSetter.setState(
                namespaceAndName,
                buildContext(currentInfo),
                StateRefreshPolicy.ALWAYS,
                stateRequestToken
            )
        }
    }

    private fun buildContext(info: TemplateDirectiveInfo?): String = JsonObject().apply {
        addProperty(
            "version",
            VERSION
        )
        info?.payload?.let {
            addProperty("playServiceId", it.playServiceId)
            addProperty("token", it.token)
        }
    }.toString()
}