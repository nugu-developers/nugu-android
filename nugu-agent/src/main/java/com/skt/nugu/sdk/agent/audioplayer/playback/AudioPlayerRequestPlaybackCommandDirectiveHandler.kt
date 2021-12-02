package com.skt.nugu.sdk.agent.audioplayer.playback

import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerPlaybackInfoProvider
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class AudioPlayerRequestPlaybackCommandDirectiveHandler(
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface,
    private val playbackInfoProvider: AudioPlayerPlaybackInfoProvider
) : AbstractDirectiveHandler() {
    companion object {
        const val NAMESPACE =
            DefaultAudioPlayerAgent.NAMESPACE
        val VERSION =
            DefaultAudioPlayerAgent.VERSION

        // v1.2
        private const val NAME_REQUEST = "Request"

        private const val NAME_RESUME = "Resume"
        private const val NAME_NEXT = "Next"
        private const val NAME_PREVIOUS = "Previous"
        private const val NAME_PAUSE = "Pause"
        private const val NAME_STOP = "Stop"

        private const val NAME_COMMAND = "Command"

        private const val NAME_ISSUED = "Issued"
        private const val NAME_FAILED = "Failed"

        private val REQUEST_RESUME_COMMAND =
            NamespaceAndName(NAMESPACE, "$NAME_REQUEST$NAME_RESUME$NAME_COMMAND")
        private val REQUEST_NEXT_COMMAND =
            NamespaceAndName(NAMESPACE, "$NAME_REQUEST$NAME_NEXT$NAME_COMMAND")
        private val REQUEST_PREVIOUS_COMMAND =
            NamespaceAndName(NAMESPACE, "$NAME_REQUEST$NAME_PREVIOUS$NAME_COMMAND")
        private val REQUEST_PAUSE_COMMAND =
            NamespaceAndName(NAMESPACE, "$NAME_REQUEST$NAME_PAUSE$NAME_COMMAND")
        private val REQUEST_STOP_COMMAND =
            NamespaceAndName(NAMESPACE, "$NAME_REQUEST$NAME_STOP$NAME_COMMAND")
    }

    enum class Type {
        INVALID_COMMAND,
        UNKNOWN,ERROR
    }

    private var handler: AudioPlayerAgentInterface.RequestCommandHandler? = null

    override fun preHandleDirective(info: DirectiveInfo) {
        // no-op
    }

    override fun handleDirective(info: DirectiveInfo) {
        setHandlingCompleted(info)
        contextGetter.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                val token = playbackInfoProvider.getToken()
                val offsetInMilliseconds = playbackInfoProvider.getOffsetInMilliseconds()
                val playServiceId = playbackInfoProvider.getPlayServiceId()

                val header = info.directive.header
                if (token.isNullOrBlank() || offsetInMilliseconds == null || playServiceId.isNullOrBlank()) {
                    val message = EventMessageRequest.Builder(
                        jsonContext,
                        header.namespace,
                        "$NAME_REQUEST$NAME_COMMAND$NAME_FAILED",
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        add("error", JsonObject().apply {
                            addProperty("type",Type.INVALID_COMMAND.name)
                            addProperty("message","Invalid State. Details: token: $token, offsetInMilliseconds: $offsetInMilliseconds, playServiceId: $playServiceId")
                        })
                    }.toString())
                        .referrerDialogRequestId(info.directive.getDialogRequestId())
                        .build()

                    messageSender.newCall(message).enqueue(null)

                    return
                }

                if (handler?.handleRequestCommand(
                        info.directive.payload,
                        info.directive.header
                    ) != true
                ) {
                    val message = EventMessageRequest.Builder(
                        jsonContext,
                        header.namespace,
                        "${header.name}$NAME_ISSUED",
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("token", token)
                        addProperty("offsetInMilliseconds", offsetInMilliseconds)
                        addProperty("playServiceId", playServiceId)
                    }.toString())
                        .referrerDialogRequestId(info.directive.getDialogRequestId())
                        .build()

                    messageSender.newCall(message).enqueue(null)
                } else {
                    val message = EventMessageRequest.Builder(
                        jsonContext,
                        header.namespace,
                        "$NAME_REQUEST$NAME_COMMAND$NAME_FAILED",
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        add("error", JsonObject().apply {
                            addProperty("type",Type.UNKNOWN.name)
                            addProperty("message","The handler refused to execute.")
                        })
                    }.toString())
                        .referrerDialogRequestId(info.directive.getDialogRequestId())
                        .build()

                    messageSender.newCall(message).enqueue(null)
                }
            }
        })
    }

    private fun setHandlingCompleted(info: DirectiveInfo) {
        info.result.setCompleted()
    }

    override fun cancelDirective(info: DirectiveInfo) {
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        val nonBlockingPolicy = BlockingPolicy.sharedInstanceFactory.get()

        this[REQUEST_RESUME_COMMAND] = nonBlockingPolicy
        this[REQUEST_NEXT_COMMAND] = nonBlockingPolicy
        this[REQUEST_PREVIOUS_COMMAND] = nonBlockingPolicy
        this[REQUEST_PAUSE_COMMAND] = nonBlockingPolicy
        this[REQUEST_STOP_COMMAND] = nonBlockingPolicy
    }

    fun setRequestCommandHandler(handler: AudioPlayerAgentInterface.RequestCommandHandler) {
        this.handler = handler
    }
}