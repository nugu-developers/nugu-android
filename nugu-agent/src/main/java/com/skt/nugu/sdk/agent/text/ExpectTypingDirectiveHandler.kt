package com.skt.nugu.sdk.agent.text

import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttribute
import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttributeStorageInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.display.InterLayerDisplayPolicyManager
import com.skt.nugu.sdk.core.interfaces.message.Header
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap
import kotlin.concurrent.withLock

internal class ExpectTypingDirectiveHandler(
    private val dialogAttributeStorage: DialogAttributeStorageInterface,
    private val controller: ExpectTypingHandlerInterface.Controller
    ) : AbstractDirectiveHandler(), ExpectTypingHandlerInterface, InterLayerDisplayPolicyManager.Listener {
    companion object {
        private const val NAME_EXPECT_TYPING = "ExpectTyping"

        val EXPECT_TYPING = NamespaceAndName(
            TextAgent.NAMESPACE,
            NAME_EXPECT_TYPING
        )
    }

    private val directives = ConcurrentHashMap<String, ExpectTypingHandlerInterface.Directive>()

    private val lock = ReentrantLock()

    private val pendingAttributes = LinkedHashMap<Header, DialogAttribute>()
    private val activatedTextAttributeKeys = Stack<Header>()

    override fun preHandleDirective(info: DirectiveInfo) {
        val payload = MessageFactory.create(info.directive.payload, ExpectTypingHandlerInterface.Payload::class.java)

        if (payload == null) {
            info.result.setFailed("Invalid Payload")
            return
        }

        lock.withLock {
            pendingAttributes[info.directive.header] =
                ExpectTypingHandlerInterface.Payload.getDialogAttribute(payload)
        }

        directives[info.directive.header.messageId] = ExpectTypingHandlerInterface.Directive(info.directive.header, payload)
    }

    override fun handleDirective(info: DirectiveInfo) {
        val directive = directives.remove(info.directive.header.messageId)

        if (directive == null) {
            lock.withLock {
                pendingAttributes.remove(info.directive.header)
            }
            info.result.setFailed("canceled: ${info.directive.header}")
            return
        }

        controller.expectTyping(directive)
        info.result.setCompleted()
    }

    override fun cancelDirective(info: DirectiveInfo) {
        lock.withLock {
            pendingAttributes.remove(info.directive.header)
        }
        directives.remove(info.directive.header.messageId)
    }

    override val configurations: Map<NamespaceAndName, BlockingPolicy> = HashMap<NamespaceAndName, BlockingPolicy>().apply {
        // only blocked by audio
        this[EXPECT_TYPING] = BlockingPolicy(BlockingPolicy.MEDIUM_AUDIO)
    }

    override fun onDisplayLayerRendered(layer: InterLayerDisplayPolicyManager.DisplayLayer) {
        lock.withLock {
            pendingAttributes.firstNotNullOfOrNull {
                if(it.key.dialogRequestId == layer.getDialogRequestId()) {
                    it
                } else {
                    null
                }
            }?.let {
                pendingAttributes.remove(it.key)
                activatedTextAttributeKeys.push(it.key)
                dialogAttributeStorage.setAttribute(it.key.messageId, it.value)
            }
        }
    }

    override fun onDisplayLayerCleared(layer: InterLayerDisplayPolicyManager.DisplayLayer) {
        lock.withLock {
            activatedTextAttributeKeys.find { it.dialogRequestId == layer.getDialogRequestId() }?.let {
                activatedTextAttributeKeys.remove(it)
                dialogAttributeStorage.removeAttribute(it.messageId)
            }
        }
    }
}