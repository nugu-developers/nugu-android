package com.skt.nugu.sdk.agent.audioplayer.playlist

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.payload.PlayStackControl
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger

class ShowPlaylistDirectiveHandler(
    private val contextManager: ContextManagerInterface,
    private val messageSender: MessageSender,
    private val visibilityController: PlaylistVisibilityController
) : AbstractDirectiveHandler() {
    companion object {
        private const val TAG = "ShowPlaylistDirectiveHandler"

        private const val NAMESPACE = DefaultAudioPlayerAgent.NAMESPACE
        private val VERSION = DefaultAudioPlayerAgent.VERSION

        private const val NAME_SHOW_PLAYLIST = "ShowPlaylist"

        private val SHOW_PLAYLIST = NamespaceAndName(NAMESPACE, NAME_SHOW_PLAYLIST)
    }

    private data class Payload(
        @SerializedName("playServiceId")
        val playServiceId: String,
        @SerializedName("playStackControl")
        val playStackControl: PlayStackControl?
    )

    interface PlaylistVisibilityController {
        fun show(playServiceId: String): Boolean
    }

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, Payload::class.java)
        if(payload == null) {
            Logger.d(TAG, "[handleDirective] invalid payload")
            setHandlingFailed(info, "[handleDirective] invalid payload")
            return
        }

        val playServiceId = payload.playServiceId
        val referrerDialogRequestId = info.directive.getDialogRequestId()

        if (visibilityController.show(playServiceId)) {
            sendEvent("${NAME_SHOW_PLAYLIST}Succeeded", playServiceId, referrerDialogRequestId)
        } else {
            sendEvent("${NAME_SHOW_PLAYLIST}Failed", playServiceId, referrerDialogRequestId)
        }

        setHandlingCompleted(info)
    }

    private fun sendEvent(name: String, playServiceId: String, referrerDialogRequestId: String) {
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(jsonContext,
                        NAMESPACE, name, VERSION.toString())
                        .payload(
                            JsonObject().apply {
                                addProperty("playServiceId", playServiceId)
                            }.toString()
                        )
                        .referrerDialogRequestId(referrerDialogRequestId)
                        .build()
                ).enqueue(null)
            }
        },NamespaceAndName("supportedInterfaces", NAMESPACE))
    }

    override fun cancelDirective(info: DirectiveInfo) {
        // no-op
    }

    private fun setHandlingFailed(info: DirectiveInfo, msg: String) {
        info.result.setFailed(msg)
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> =
        HashMap<NamespaceAndName, BlockingPolicy>().apply {
            this[SHOW_PLAYLIST] = BlockingPolicy.sharedInstanceFactory.get()
        }
}