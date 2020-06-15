package com.skt.nugu.sdk.agent.ext.mediaplayer.handler

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.ext.mediaplayer.MediaPlayerAgent
import com.skt.nugu.sdk.agent.ext.mediaplayer.payload.GetInfoPayload
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class GetInfoDirectiveHandler(
    private val controller: Controller,
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface
): AbstractDirectiveHandler() {
    companion object {
        private const val NAME_GET_INFO = "GetInfo"
        private const val NAME_RESPONSE_INFO = "ResponseInfo"

        private val GET_INFO = NamespaceAndName(MediaPlayerAgent.NAMESPACE, NAME_GET_INFO)
    }

    interface Controller {
        fun getInfo(payload: GetInfoPayload): Map<GetInfoPayload.InfoItem, String>?
    }

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, GetInfoPayload::class.java)
        if(payload == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            info.result.setCompleted()
            val infos = controller.getInfo(payload)
            if(infos != null) {
                contextGetter.getContext(object: IgnoreErrorContextRequestor() {
                    override fun onContext(jsonContext: String) {
                        messageSender.sendMessage(
                            EventMessageRequest.Builder(
                                jsonContext,
                                MediaPlayerAgent.NAMESPACE,
                                NAME_RESPONSE_INFO,
                                MediaPlayerAgent.VERSION.toString()
                            ).payload(JsonObject().apply {
                                addProperty("playServiceId", payload.playServiceId)
                                addProperty("token", payload.token)
                                add("infos", JsonObject().apply {
                                    infos.forEach {
                                        addProperty(it.key.value, it.value)
                                    }
                                })
                            }.toString())
                                .referrerDialogRequestId(info.directive.getDialogRequestId())
                                .build()
                        )
                    }
                })
            }
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[GET_INFO] = BlockingPolicy()

        return configuration
    }
}