package com.skt.nugu.sdk.agent.text

import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import java.util.concurrent.ConcurrentHashMap

internal class ExpectTypingDirectiveHandler(
    private val textAttributeStorage: TextAttributeStorage,
    private val controller: ExpectTypingHandlerInterface.Controller
    ) :
    AbstractDirectiveHandler(), ExpectTypingHandlerInterface {
    companion object {
        private const val NAME_EXPECT_TYPING = "ExpectTyping"

        val EXPECT_TYPING = NamespaceAndName(
            TextAgent.NAMESPACE,
            NAME_EXPECT_TYPING
        )
    }

    private val directives = ConcurrentHashMap<String, ExpectTypingHandlerInterface.Directive>()

    override fun preHandleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, ExpectTypingHandlerInterface.Payload::class.java)

        if (payload == null) {
            info.result.setFailed("Invalid Payload")
            return
        }

        textAttributeStorage.setAttributes(
            info.directive.header.dialogRequestId,
            HashMap<String, Any>().apply {
                put("playServiceId", payload.playServiceId)
                payload.domainTypes?.let {
                    put("domainTypes", it)
                }
            })

        directives[info.directive.header.messageId] = ExpectTypingHandlerInterface.Directive(info.directive.header, payload)
    }

    override fun handleDirective(info: DirectiveInfo) {
        val directive = directives.remove(info.directive.header.messageId)

        if (directive == null) {
            textAttributeStorage.removeAttributes(info.directive.header.dialogRequestId)
            info.result.setFailed("canceled: ${info.directive.header}")
            return
        }

        controller.expectTyping(directive)
        info.result.setCompleted()
    }

    override fun cancelDirective(info: DirectiveInfo) {
        textAttributeStorage.removeAttributes(info.directive.header.dialogRequestId)
        directives.remove(info.directive.header.messageId)
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> =
        HashMap<NamespaceAndName, BlockingPolicy>().apply {
            // only blocked by audio
            put(
                EXPECT_TYPING, BlockingPolicy(
                    BlockingPolicy.MEDIUM_AUDIO
                )
            )
        }
}