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
package com.skt.nugu.sdk.core.directivesequencer

import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.LoopThread
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.withLock

class DirectiveProcessor(
    private val directiveRouter: DirectiveRouter
) {
    companion object {
        private const val TAG = "DirectiveProcessor"
    }

    private inner class DirectiveHandlerResult(
        private val directive: Directive
    ) : com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult {
        override fun setCompleted() {
            listeners.forEach {
                it.onCompleted(directive)
            }
            onHandlingCompleted(directive)
        }

        override fun setFailed(description: String, cancelAll: Boolean) {
            listeners.forEach {
                it.onFailed(directive, description)
            }

            onHandlingFailed(directive, description, cancelAll)
        }
    }

    private data class DirectiveAndPolicy(
        val directive: Directive,
        val policy: BlockingPolicy
    )

    private var directiveBeingPreHandled: Directive? = null
    private val directivesBeingHandled: MutableMap<BlockingPolicy.Medium, Directive?> = HashMap()
    private var cancelingQueue = ArrayDeque<Directive>()
    private var handlingQueue = ArrayDeque<DirectiveAndPolicy>()
    private val directiveLock = ReentrantLock()
    private val lock = ReentrantLock()
    private val processingLoop: LoopThread = object : LoopThread() {
        override fun onLoop() {
            lock.withLock {
                var cancelHandled: Boolean
                var queuedHandled: Boolean
                do {
                    cancelHandled = processCancelingQueueLocked()
                    queuedHandled = handleQueuedDirectivesLocked()
                } while (cancelHandled || queuedHandled)
            }
        }
    }
    private var isEnabled = true

    private var listeners = CopyOnWriteArraySet<DirectiveSequencerInterface.OnDirectiveHandlingListener>()

    init {
        processingLoop.start()
    }

    fun addOnDirectiveHandlingListener(listener: DirectiveSequencerInterface.OnDirectiveHandlingListener) {
        listeners.add(listener)
    }

    fun removeOnDirectiveHandlingListener(listener: DirectiveSequencerInterface.OnDirectiveHandlingListener) {
        listeners.remove(listener)
    }

    fun onDirective(directive: Directive): Boolean {
        Logger.d(TAG, "[onDirective] $directive")
        directiveLock.withLock {
            lock.withLock {
                if (shouldDropDirective(directive)) {
                    Logger.d(TAG, "[onDirective] drop directive : $directive")
                    return true
                }

                directiveBeingPreHandled = directive
            }

            val preHandled = directiveRouter.preHandleDirective(directive, DirectiveHandlerResult(directive))

            lock.withLock {
                if (directiveBeingPreHandled == null && preHandled) {
                    Logger.d(TAG, "[onDirective] directive handling completed at preHandleDirective().")
                    return true
                }

                directiveBeingPreHandled = null

                if (!preHandled) {
                    return false
                }

                handlingQueue.offer(DirectiveAndPolicy(directive, directiveRouter.getPolicy(directive)))
                processingLoop.wakeAll()
                return true
            }
        }
    }

    private fun shouldDropDirective(directive: Directive): Boolean = false // for test set to false
    //directive.getDialogRequestId().isNotEmpty() && directive.getDialogRequestId() != dialogRequestId

    private fun scrubDialogRequestIdLocked(dialogRequestId: String) {
        if (dialogRequestId.isEmpty()) {
            Logger.d(TAG, "[scrubDialogRequestIdLocked] emptyDialogRequestId")
            return
        }

        Logger.d(TAG, "[scrubDialogRequestIdLocked] dialogRequestId : $dialogRequestId")

        var changed = cancelDirectiveBeingPreHandledLocked()


        val freed = clearDirectiveBeingHandledLocked {
            it.getDialogRequestId() == dialogRequestId
        }

        if (freed.isNotEmpty()) {
            cancelingQueue.addAll(freed)
            changed = true
        }

        // Filter matching directives from m_handlingQueue and put them in m_cancelingQueue.
        val temp = ArrayDeque<DirectiveAndPolicy>()
        for (directiveAndPolicy in handlingQueue) {
            val id = directiveAndPolicy.directive.getDialogRequestId()
            if (id.isNotEmpty() && id == dialogRequestId) {
                cancelingQueue.offer(directiveAndPolicy.directive)
                changed = true
            } else {
                temp.offer(directiveAndPolicy)
            }
        }
        handlingQueue = temp
    }

    private fun cancelDirectiveBeingPreHandledLocked(): Boolean {
        directiveBeingPreHandled?.let {
            val id = it.getDialogRequestId()
            if (id.isNotEmpty()) {
                cancelingQueue.offer(directiveBeingPreHandled)
                directiveBeingPreHandled = null
                return true
            }
        }

        return false
    }

    private fun clearDirectiveBeingHandledLocked(policy: BlockingPolicy) {
        if (policy.mediums.audio && directivesBeingHandled[BlockingPolicy.Medium.AUDIO] != null) {
            directivesBeingHandled[BlockingPolicy.Medium.AUDIO] = null
        }

        if (policy.mediums.visual && directivesBeingHandled[BlockingPolicy.Medium.VISUAL] != null) {
            directivesBeingHandled[BlockingPolicy.Medium.VISUAL] = null
        }
    }

    private fun clearDirectiveBeingHandledLocked(shouldClear: (Directive) -> Boolean): Set<Directive> {
        val freed = HashSet<Directive>()

        var directive = directivesBeingHandled[BlockingPolicy.Medium.AUDIO]
        if (directive != null && shouldClear(directive)) {
            freed.add(directive)
            directivesBeingHandled[BlockingPolicy.Medium.AUDIO] = null
        }

        directive = directivesBeingHandled[BlockingPolicy.Medium.VISUAL]
        if (directive != null && shouldClear(directive)) {
            freed.add(directive)
            directivesBeingHandled[BlockingPolicy.Medium.VISUAL] = null
        }

        return freed
    }

    fun disable() {
        lock.withLock {
            scrubDialogRequestIdLocked("")
            isEnabled = false
            queueAllDirectivesForCancellationLocked()
            processingLoop.wakeAll()
        }
    }

    fun enable(): Boolean {
        lock.withLock {
            isEnabled = true
            return true
        }
    }

    private fun onHandlingCompleted(directive: Directive) {
        Logger.d(TAG, "[onHandlingCompleted] messageId: ${directive.getMessageId()}")
        lock.withLock {
            removeDirectiveLocked(directive)
        }
    }

    private fun onHandlingFailed(directive: Directive, description: String, cancelAll: Boolean) {
        Logger.d(TAG, "[onHandlingFailed] messageId: ${directive.getMessageId()} ,description : $description, cancelAll: $cancelAll")
        lock.withLock {
            removeDirectiveLocked(directive)
            if(cancelAll) {
                scrubDialogRequestIdLocked(directive.getDialogRequestId())
            }
        }
    }

    private fun removeDirectiveLocked(directive: Directive) {
        cancelingQueue.remove(directive)

        if (directiveBeingPreHandled == directive) {
            directiveBeingPreHandled = null
        }

        for (directiveAndPolicy in handlingQueue) {
            if (directiveAndPolicy.directive == directive) {
                handlingQueue.remove(directiveAndPolicy)
                break
            }
        }

        if (directivesBeingHandled[BlockingPolicy.Medium.AUDIO] != null && directivesBeingHandled[BlockingPolicy.Medium.AUDIO] == directive) {
            Logger.d(TAG, "[removeDirectiveLocked] audio blocking lock removed")
            directivesBeingHandled[BlockingPolicy.Medium.AUDIO] = null
        }

        if (directivesBeingHandled[BlockingPolicy.Medium.VISUAL] != null && directivesBeingHandled[BlockingPolicy.Medium.VISUAL] == directive) {
            directivesBeingHandled[BlockingPolicy.Medium.VISUAL] = null
        }

        if (cancelingQueue.isNotEmpty() || handlingQueue.isNotEmpty()) {
            processingLoop.wakeOne()
        }
    }

    private fun processCancelingQueueLocked(): Boolean {
        Logger.d(TAG, "[processCancelingQueueLocked] cancelingQueue size : ${cancelingQueue.size}")
        if (cancelingQueue.isEmpty()) {
            return false
        }

        val copyCancelingQueue = cancelingQueue
        cancelingQueue = ArrayDeque()
        lock.unlock()
        for (directive in copyCancelingQueue) {
            directiveRouter.cancelDirective(directive)
            listeners.forEach {
                it.onCanceled(directive)
            }
        }
        lock.lock()
        return true
    }

    private fun handleQueuedDirectivesLocked(): Boolean {
        if (handlingQueue.isEmpty()) {
            return false
        }

        var handleDirectiveCalled = false

        Logger.d(TAG, "[handleQueuedDirectivesLocked] handlingQueue size : ${handlingQueue.size}")

        while (handlingQueue.isNotEmpty()) {
            val it = getNextUnblockedDirectiveLocked()

            if (it == null) {
                Logger.d(TAG, "[handleQueuedDirectivesLocked] all queued directives are blocked $directivesBeingHandled")
                break
            }

            val directive = it.directive
            val policy = it.policy

            // if policy is not blocking, then don't set???
            setDirectiveBeingHandledLocked(directive, policy)
            handlingQueue.remove(it)

            handleDirectiveCalled = true
            lock.unlock()
            listeners.forEach {
                it.onRequested(directive)
            }
            val handleDirectiveSucceeded = directiveRouter.handleDirective(directive)
            if(!handleDirectiveSucceeded) {
                listeners.forEach {
                    it.onFailed(directive, "no handler for directive")
                }
            }
            lock.lock()

            // if handle failed or directive is not blocking
            if (!handleDirectiveSucceeded || !policy.isBlocking) {
                clearDirectiveBeingHandledLocked(policy)
            }

            if (!handleDirectiveSucceeded) {
                Logger.e(
                    TAG,
                    "[handleQueuedDirectivesLocked] handleDirectiveFailed message id : ${directive.getMessageId()}"
                )
                scrubDialogRequestIdLocked(directive.getDialogRequestId())
            }
        }

        return handleDirectiveCalled
    }

    private fun setDirectiveBeingHandledLocked(directive: Directive, policy: BlockingPolicy) {
        if (policy.mediums.audio) {
            directivesBeingHandled[BlockingPolicy.Medium.AUDIO] = directive
        }

        if (policy.mediums.visual) {
            directivesBeingHandled[BlockingPolicy.Medium.VISUAL] = directive
        }
    }

    private fun getNextUnblockedDirectiveLocked(): DirectiveAndPolicy? {
        // A medium is considered blocked if a previous blocking directive hasn't been completed yet.
        val blockedMediums: MutableMap<BlockingPolicy.Medium, Boolean> = HashMap()

        // Mark mediums used by blocking directives being handled as blocked.
        blockedMediums[BlockingPolicy.Medium.AUDIO] = directivesBeingHandled[BlockingPolicy.Medium.AUDIO] != null
        blockedMediums[BlockingPolicy.Medium.VISUAL] = directivesBeingHandled[BlockingPolicy.Medium.VISUAL] != null

        Logger.d(TAG, "[getNextUnblockedDirectiveLocked] block mediums : ${directivesBeingHandled[BlockingPolicy.Medium.AUDIO]}/${directivesBeingHandled[BlockingPolicy.Medium.VISUAL]}")

        for (directiveAndPolicy in handlingQueue) {
            val currentUsingAudio = directiveAndPolicy.policy.mediums.audio
            val currentUsingVisual = directiveAndPolicy.policy.mediums.visual

            if ((currentUsingAudio && blockedMediums[BlockingPolicy.Medium.AUDIO]!!) ||
                (currentUsingVisual && blockedMediums[BlockingPolicy.Medium.VISUAL]!!)
            ) {
                // if the current directive is blocking, block its Mediums.
                if (directiveAndPolicy.policy.isBlocking) {
                    blockedMediums[BlockingPolicy.Medium.AUDIO] =
                        (blockedMediums[BlockingPolicy.Medium.AUDIO]!! || currentUsingAudio)
                    blockedMediums[BlockingPolicy.Medium.VISUAL] =
                        (blockedMediums[BlockingPolicy.Medium.AUDIO]!! || currentUsingVisual)
                }
            } else {
                return directiveAndPolicy
            }
        }

        return null
    }

    private fun queueAllDirectivesForCancellationLocked() {
        var changed = false
        val freed = clearDirectiveBeingHandledLocked { true }

        if(freed.isNotEmpty()) {
            changed = true
            cancelingQueue.addAll(freed)
        }

        if(handlingQueue.isNotEmpty()) {
            handlingQueue.forEach {
                cancelingQueue.add(it.directive)
            }

            changed = true
            handlingQueue.clear()
        }

        if(changed) {
            // 처리가 필요한 내용이 있으면 processingLoop을 wake
            processingLoop.wakeAll()
        }
    }
}