package com.skt.nugu.sdk.core.interfaces.capability.movement

import com.skt.nugu.sdk.core.interfaces.capability.AbstractCapabilityAgent
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender

abstract class AbstractMovementAgent(
    protected val contextManager: ContextManagerInterface,
    protected val messageSender: MessageSender,
    protected val movementController: MovementController
) : AbstractCapabilityAgent() {
    companion object {
        const val NAMESPACE = "Movement"
        const val VERSION = "1.0"
    }
}