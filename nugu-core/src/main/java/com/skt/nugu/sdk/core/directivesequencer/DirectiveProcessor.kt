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

import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveProcessorInterface
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
): DirectiveProcessorInterface {
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

        override fun setFailed(
            description: String,
            cancelPolicy: com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult.CancelPolicy
        ) {
            listeners.forEach {
                it.onFailed(directive, description)
            }

            onHandlingFailed(directive, description, cancelPolicy)
        }
    }

    private data class DirectiveAndPolicy(
        val directive: Directive,
        val policy: BlockingPolicy
    )

    private var directiveBeingPreHandled: Directive? = null
    private val directivesBeingHandled: MutableMap<String, MutableMap<BlockingPolicy.Medium, Directive>> = HashMap()
    private var cancelingQueue = ArrayDeque<Directive>()
    private var handlingQueue = ArrayDeque<DirectiveAndPolicy>()
    private val directiveLock = ReentrantLock()
    private val lock = ReentrantLock()
    private val processingLoop: LoopThread = object : LoopThread() {
        override fun onLoop() {
            var cancelHandled: Boolean
            var queuedHandled: Boolean
            do {
                cancelHandled = processCancelingQueue()
                queuedHandled = handleQueuedDirectives()
            } while (cancelHandled || queuedHandled)
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
                directiveBeingPreHandled = directive
            }

            val preHandled = directiveRouter.preHandleDirective(directive, DirectiveHandlerResult(directive))

            if(!preHandled) {
                listeners.forEach {
                    it.onSkipped(directive)
                }
            }

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

    private fun scrubWithConditionLocked(shouldClear: (Directive) -> Boolean) {
        Logger.d(TAG, "[scrubWithConditionLocked]")

        var changed = cancelDirectiveBeingPreHandledLocked(shouldClear)

        val freed = clearDirectiveBeingHandledLocked(shouldClear)

        if (freed.isNotEmpty()) {
            cancelingQueue.addAll(freed)
            changed = true
        }

        // Filter matching directives from m_handlingQueue and put them in m_cancelingQueue.
        val temp = ArrayDeque<DirectiveAndPolicy>()
        for (directiveAndPolicy in handlingQueue) {
            if(shouldClear(directiveAndPolicy.directive)) {
                cancelingQueue.offer(directiveAndPolicy.directive)
                changed = true
            } else {
                temp.offer(directiveAndPolicy)
            }
        }
        handlingQueue = temp
    }

    private fun scrubDialogRequestIdLocked(dialogRequestId: String) {
        if (dialogRequestId.isEmpty()) {
            Logger.d(TAG, "[scrubDialogRequestIdLocked] emptyDialogRequestId")
            return
        }

        Logger.d(TAG, "[scrubDialogRequestIdLocked] dialogRequestId : $dialogRequestId")

        scrubWithConditionLocked{ directive: Directive ->
            directive.getDialogRequestId() == dialogRequestId
        }
    }

    override fun cancelDialogRequestId(dialogRequestId: String) {
        lock.withLock {
            scrubDialogRequestIdLocked(dialogRequestId)
        }
    }

    private fun scrubDialogRequestIdWithTargetsLocked(dialogRequestId: String, scrubTargets: Set<NamespaceAndName>) {
        if (dialogRequestId.isEmpty()) {
            Logger.d(TAG, "[scrubDialogRequestIdWithTargetsLocked] emptyDialogRequestId")
            return
        }

        if(scrubTargets.isEmpty()) {
            Logger.d(TAG, "[scrubDialogRequestIdWithTargetsLocked] empty targets")
            return
        }

        Logger.d(TAG, "[scrubDialogRequestIdWithTargetsLocked] dialogRequestId : $dialogRequestId")

        scrubWithConditionLocked{ directive: Directive ->
            directive.getDialogRequestId() == dialogRequestId && scrubTargets.contains(directive.getNamespaceAndName())
        }
    }

    private fun cancelDirectiveBeingPreHandledLocked(shouldClear: (Directive) -> Boolean): Boolean {
        directiveBeingPreHandled?.let {
            if(shouldClear(it)){
                cancelingQueue.offer(it)
                directiveBeingPreHandled = null
                return true
            }
        }

        return false
    }

    private fun clearDirectiveBeingHandledLocked(dialogRequestId: String, policy: BlockingPolicy) {
        directivesBeingHandled[dialogRequestId]?.let {
            EnumSet.allOf(BlockingPolicy.Medium::class.java).forEach { medium ->
                if(policy.blocking?.contains(medium)==true && it[medium] != null) {
                    it.remove(medium)
                }
            }

            if(it.isEmpty()) {
                directivesBeingHandled.remove(dialogRequestId)
            }
        }
    }

    private fun clearDirectiveBeingHandledLocked(shouldClear: (Directive) -> Boolean): Set<Directive> {
        val freed = HashSet<Directive>()

        directivesBeingHandled.forEach {
            EnumSet.allOf(BlockingPolicy.Medium::class.java).forEach { medium ->
                val directive = it.value[medium]
                if (directive != null && shouldClear(directive)) {
                    freed.add(directive)
                    it.value.remove(medium)
                }
            }
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

    private fun onHandlingFailed(directive: Directive, description: String, cancelPolicy: com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult.CancelPolicy) {
        Logger.d(TAG, "[onHandlingFailed] messageId: ${directive.getMessageId()} ,description : $description, cancelPolicy: $cancelPolicy")
        lock.withLock {
            removeDirectiveLocked(directive)

            if(cancelPolicy.cancelAll) {
                scrubDialogRequestIdLocked(directive.getDialogRequestId())
            } else {
                cancelPolicy.partialTargets?.let {
                    scrubDialogRequestIdWithTargetsLocked(directive.getDialogRequestId(), it)
                }
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

        directivesBeingHandled[directive.getDialogRequestId()]?.let {
            EnumSet.allOf(BlockingPolicy.Medium::class.java).forEach { medium ->
                if (it[medium] == directive) {
                    Logger.d(TAG, "[removeDirectiveLocked] $medium blocking lock removed")
                    it.remove(medium)
                }
            }

            if(it.isEmpty()) {
                directivesBeingHandled.remove(directive.getDialogRequestId())
            }
        }

        if (cancelingQueue.isNotEmpty() || handlingQueue.isNotEmpty()) {
            processingLoop.wakeOne()
        }
    }

    private fun processCancelingQueue(): Boolean {
        val copyCancelingQueue = lock.withLock {
            Logger.d(
                TAG,
                "[processCancelingQueueLocked] cancelingQueue size : ${cancelingQueue.size}"
            )
            if (cancelingQueue.isEmpty()) {
                return false
            }

            val copyCancelingQueue = cancelingQueue
            cancelingQueue = ArrayDeque()
            copyCancelingQueue
        }

        for (directive in copyCancelingQueue) {
            directiveRouter.cancelDirective(directive)
            listeners.forEach {
                it.onCanceled(directive)
            }
        }
        return true
    }

    private fun handleQueuedDirectives(): Boolean {
        lock.lock()

        if (handlingQueue.isEmpty()) {
            lock.unlock()
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
            if (!handleDirectiveSucceeded || (policy.blocking == null || policy.blocking?.isEmpty() == true)) {
                clearDirectiveBeingHandledLocked(directive.getDialogRequestId(), policy)
            }

            if (!handleDirectiveSucceeded) {
                Logger.e(
                    TAG,
                    "[handleQueuedDirectivesLocked] handleDirectiveFailed message id : ${directive.getMessageId()}"
                )
                scrubDialogRequestIdLocked(directive.getDialogRequestId())
            }
        }

        lock.unlock()
        return handleDirectiveCalled
    }

    private fun setDirectiveBeingHandledLocked(directive: Directive, policy: BlockingPolicy) {
        val key = directive.getDialogRequestId()

        EnumSet.allOf(BlockingPolicy.Medium::class.java).forEach { medium ->
            if(policy.blocking?.contains(medium) == true) {
                var map = directivesBeingHandled[key]
                if(map == null) {
                    map = HashMap()
                    directivesBeingHandled[key] = map
                }
                map[medium] = directive
            }
        }
    }

    private fun getNextUnblockedDirectiveLocked(): DirectiveAndPolicy? {
        // A medium is considered blocked if a previous blocking directive hasn't been completed yet.
        val blockedMediumsMap: MutableMap<String, MutableMap<BlockingPolicy.Medium, Boolean>> = HashMap()

        // Mark mediums used by blocking directives being handled as blocked.
        directivesBeingHandled.forEach {src ->
            blockedMediumsMap[src.key] = HashMap<BlockingPolicy.Medium, Boolean>().also { dst->
                src.value.forEach {
                    dst[it.key] = true
                }
            }
        }

        Logger.d(TAG, "[getNextUnblockedDirectiveLocked] block mediums : $blockedMediumsMap")

        for (directiveAndPolicy in handlingQueue) {
            val blockedMediums = blockedMediumsMap[directiveAndPolicy.directive.getDialogRequestId()]
                ?: return directiveAndPolicy

            val policy = directiveAndPolicy.policy

            val blocked = policy.blockedBy?.any {
                blockedMediums[it] == true
            } ?: false

            policy.blocking?.forEach {
                blockedMediums[it] = true
            }

            if(!blocked) {
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