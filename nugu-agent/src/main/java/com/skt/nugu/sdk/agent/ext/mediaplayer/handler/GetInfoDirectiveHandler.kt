package com.skt.nugu.sdk.agent.ext.mediaplayer.handler

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.ext.mediaplayer.MediaPlayerAgent
import com.skt.nugu.sdk.agent.ext.mediaplayer.Song
import com.skt.nugu.sdk.agent.ext.mediaplayer.event.GetInfoCallback
import com.skt.nugu.sdk.agent.ext.mediaplayer.payload.Payload
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class GetInfoDirectiveHandler(
    private val controller: Controller,
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface
) : AbstractDirectiveHandler() {
    companion object {
        private const val NAME_GET_INFO = "GetInfo"
        private const val NAME_SUCCEEDED = "Succeeded"
        private const val NAME_FAILED = "Failed"

        private val GET_INFO = NamespaceAndName(MediaPlayerAgent.NAMESPACE, NAME_GET_INFO)
    }

    interface Controller {
        fun getInfo(header: Header, payload: Payload, callback: GetInfoCallback)
    }

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, Payload::class.java)
        if (payload == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            info.result.setCompleted()
            controller.getInfo(info.directive.header, payload, object : GetInfoCallback {
                override fun onSuccess(
                    song: Song?,
                    issueDate: String?,
                    playTime: String?,
                    playListName: String?
                ) {
                    contextGetter.getContext(object: IgnoreErrorContextRequestor() {
                        override fun onContext(jsonContext: String) {
                            messageSender.newCall(
                                EventMessageRequest.Builder(
                                    jsonContext,
                                    MediaPlayerAgent.NAMESPACE,
                                    "${NAME_GET_INFO}${NAME_SUCCEEDED}",
                                    MediaPlayerAgent.VERSION.toString()
                                ).payload(JsonObject().apply {
                                    addProperty("playServiceId", payload.playServiceId)
                                    addProperty("token", payload.token)
                                    add("info", JsonObject().apply {
                                        song?.let {
                                            add("song", song.toJson())
                                        }
                                        issueDate?.let {
                                            addProperty("issueDate", issueDate)
                                        }
                                        playTime?.let {
                                            addProperty("playTime", playTime)
                                        }
                                        playListName?.let {
                                            addProperty("playListName", playListName)
                                        }
                                    })
                                }.toString())
                                    .referrerDialogRequestId(info.directive.getDialogRequestId())
                                    .build()
                            ).enqueue(null)
                        }
                    })
                }

                override fun onFailure(errorCode: String?) {
                    contextGetter.getContext(object: IgnoreErrorContextRequestor() {
                        override fun onContext(jsonContext: String) {
                            messageSender.newCall(
                                EventMessageRequest.Builder(
                                    jsonContext,
                                    MediaPlayerAgent.NAMESPACE,
                                    "${NAME_GET_INFO}${NAME_FAILED}",
                                    MediaPlayerAgent.VERSION.toString()
                                ).payload(JsonObject().apply {
                                    addProperty("playServiceId", payload.playServiceId)
                                    addProperty("token", payload.token)
                                    errorCode?.let {
                                        addProperty("errorCode", errorCode)
                                    }
                                }.toString())
                                    .referrerDialogRequestId(info.directive.getDialogRequestId())
                                    .build()
                            ).enqueue(null)
                        }
                    })
                }
            })
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        this[GET_INFO] = BlockingPolicy.sharedInstanceFactory.get()
    }
}