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
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandler
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.LoopThread
import com.skt.nugu.sdk.core.utils.getAsyncKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.withLock

private fun getBlockingKey(directive: Directive): String = directive.payload.getAsyncKey()?.eventDialogRequestId ?: directive.getDialogRequestId()

class DirectiveProcessor(
    private val directiveRouter: DirectiveRouter
) : DirectiveProcessorInterface {
    companion object {
        private const val TAG = "DirectiveProcessor"
    }

    private inner class DirectiveHandlerResult(
        private val directive: Directive
    ) : com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult {
        override fun setCompleted() {
            Logger.d(TAG, "[setCompleted] directive: ${directive.getMessageId()}")
            listeners.forEach {
                it.onCompleted(directive)
            }
            onHandlingCompleted(directive)
        }

        override fun setFailed(
            description: String,
            cancelPolicy: com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult.CancelPolicy
        ) {
            Logger.d(TAG, "[setFailed] directive: ${directive.getMessageId()}, description: $description, cancelPolicy: $cancelPolicy")
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

    private class BlockingDirectiveStore {
        private val blockingObjects: ConcurrentHashMap<String, MutableMap<BlockingPolicy.Medium, Directive>> = ConcurrentHashMap()

        fun get() = blockingObjects

        fun setDirectiveBeingHandledLocked(directive: Directive, policy: BlockingPolicy) {
            val blocking = policy.blocking ?: return

            val key = getBlockingKey(directive)

            BlockingPolicy.MEDIUM_ALL.forEach { medium ->
                if (blocking.contains(medium)) {
                    val map = blockingObjects[key] ?: HashMap()
                    map[medium] = directive
                    blockingObjects[key] = map
                }
            }
        }


        fun clearDirectiveBeingHandledLocked(directive: Directive, policy: BlockingPolicy) {
            val key = getBlockingKey(directive)
            blockingObjects[key]?.let {
                BlockingPolicy.MEDIUM_ALL.forEach { medium ->
                    if (policy.blocking?.contains(medium) == true && it[medium] != null) {
                        it.remove(medium)
                    }
                }

                if (it.isEmpty()) {
                    blockingObjects.remove(key)
                }
            }
        }

        fun clearDirectiveBeingHandledLocked(shouldClear: (Directive) -> Boolean): Set<Directive> {
            val freed = HashSet<Directive>()

            blockingObjects.values.forEach {
                BlockingPolicy.MEDIUM_ALL.forEach { medium ->
                    it[medium]?.let { directive ->
                        if (shouldClear(directive)) {
                            freed.add(directive)
                            it.remove(medium)
                        }

                        if (it.isEmpty()) {
                            blockingObjects.remove(getBlockingKey(directive))
                        }
                    }
                }
            }

            return freed
        }

        fun removeDirective(directive: Directive) {
            val key = getBlockingKey(directive)

            blockingObjects[key]?.let {
                BlockingPolicy.MEDIUM_ALL.forEach { medium ->
                    if (it[medium] == directive) {
                        Logger.d(TAG, "[removeDirectiveLocked] $medium blocking lock removed")
                        it.remove(medium)
                    }
                }

                if (it.isEmpty()) {
                    blockingObjects.remove(key)
                }
            }
        }
    }

    private var directiveBeingPreHandled: Directive? = null
    private val blockingDirectiveStore: BlockingDirectiveStore = BlockingDirectiveStore()
    private var cancelingQueue = ArrayDeque<Directive>()
    private var handlingQueue = ArrayDeque<DirectiveAndPolicy>()
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
    private val directiveProcessBlocker = CopyOnWriteArraySet<DirectiveProcessorInterface.Blocker>()

    init {
        processingLoop.start()
    }

    fun addOnDirectiveHandlingListener(listener: DirectiveSequencerInterface.OnDirectiveHandlingListener) {
        listeners.add(listener)
    }

    fun removeOnDirectiveHandlingListener(listener: DirectiveSequencerInterface.OnDirectiveHandlingListener) {
        listeners.remove(listener)
    }

    fun onDirectives(directives: List<Directive>) {
        val shouldBeHandleDirectives = ArrayList<DirectiveAndPolicy>()

        directives.forEach { directive ->
            val handlerAndPolicy = directiveRouter.getHandlerAndPolicyOfDirective(directive)
            if(handlerAndPolicy != null) {
                preHandleDirective(directive, handlerAndPolicy.first)
                shouldBeHandleDirectives.add(DirectiveAndPolicy(directive, handlerAndPolicy.second))
            } else {
                Logger.w(TAG, "[onDirectives] no handler for ${directive.getNamespaceAndName()}")
                listeners.forEach {
                    it.onSkipped(directive)
                }
            }
        }

        lock.withLock {
            shouldBeHandleDirectives.forEach {
                handlingQueue.offer(it)
            }
        }

        processingLoop.wakeAll()
    }

    private fun preHandleDirective(directive: Directive, handler: DirectiveHandler) {
        Logger.d(TAG, "[onDirective] $directive")
        lock.withLock {
            directiveBeingPreHandled = directive
        }

        handler.preHandleDirective(directive, DirectiveHandlerResult(directive))

        lock.withLock {
            if (directiveBeingPreHandled == null) {
                Logger.d(TAG, "[onDirective] directive handling completed at preHandleDirective().")
            } else {
                directiveBeingPreHandled = null
            }
        }
    }

    override fun cancelDialogRequestId(dialogRequestId: String) {
        lock.withLock {
            scrub(dialogRequestId)
        }
    }

    override fun addDirectiveBlocker(blocker: DirectiveProcessorInterface.Blocker) {
        Logger.d(TAG, "[addDirectiveBlocker: $blocker]")
        directiveProcessBlocker.add(blocker)
    }

    override fun removeDirectiveBlocker(blocker: DirectiveProcessorInterface.Blocker) {
        Logger.d(TAG, "[removeDirectiveBlocker: $blocker]")
        if(directiveProcessBlocker.remove(blocker)) {
            processingLoop.wakeOne()
        }
    }

    private fun scrub(dialogRequestId: String, targets: Set<NamespaceAndName>? = null) {
        Logger.d(TAG, "[scrub] dialogRequestId $dialogRequestId, target ${targets.toString()}")
        if (dialogRequestId.isEmpty()) {
            return
        }

        scrub { directive: Directive ->
            directive.getDialogRequestId() == dialogRequestId && targets?.contains(directive.getNamespaceAndName()) ?: true
        }
    }

    private fun scrub(shouldClear: (Directive) -> Boolean) {
        var changed = cancelDirectiveBeingPreHandledLocked(shouldClear)
        val freed = blockingDirectiveStore.clearDirectiveBeingHandledLocked(shouldClear)

        if (freed.isNotEmpty()) {
            cancelingQueue.addAll(freed)
            changed = true
        }

        handlingQueue.filter { shouldClear(it.directive) }.let { cancelTargets ->
            cancelTargets.forEach {
                cancelingQueue.offer(it.directive)
                changed = true
            }

            handlingQueue.removeAll(cancelTargets)
        }

        if (changed) processingLoop.wakeAll()
    }

    private fun cancelDirectiveBeingPreHandledLocked(shouldClear: (Directive) -> Boolean): Boolean {
        directiveBeingPreHandled?.let {
            if (shouldClear(it)) {
                cancelingQueue.offer(it)
                directiveBeingPreHandled = null
                return true
            }
        }

        return false
    }

    fun disable() {
        lock.withLock {
            isEnabled = false
            scrub { true }
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

    private fun onHandlingFailed(
        directive: Directive,
        description: String,
        cancelPolicy: com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult.CancelPolicy
    ) {
        Logger.d(TAG, "[onHandlingFailed] messageId: ${directive.getMessageId()} ,description : $description, cancelPolicy: $cancelPolicy")
        lock.withLock {
            removeDirectiveLocked(directive)
            if (cancelPolicy.cancelAll) {
                scrub(directive.getDialogRequestId())
            } else {
                if (!cancelPolicy.partialTargets.isNullOrEmpty()) {
                    scrub(directive.getDialogRequestId(), cancelPolicy.partialTargets)
                }
            }
        }
    }

    private fun removeDirectiveLocked(directive: Directive) {
        cancelingQueue.remove(directive)

        if (directiveBeingPreHandled == directive) {
            directiveBeingPreHandled = null
        }

        handlingQueue.find { it.directive == directive }?.run { handlingQueue.remove(this) }

        blockingDirectiveStore.removeDirective(directive)

        if (cancelingQueue.isNotEmpty() || handlingQueue.isNotEmpty()) {
            processingLoop.wakeOne()
        }
    }

    private fun processCancelingQueue(): Boolean {
        lateinit var copyCancelingQueue: ArrayDeque<Directive>

        lock.withLock {
            Logger.d(
                TAG,
                "[processCancelingQueueLocked] cancelingQueue size : ${cancelingQueue.size}"
            )

            if (cancelingQueue.isEmpty()) return false

            copyCancelingQueue = cancelingQueue.clone()
            cancelingQueue.clear()
        }

        for (directive in copyCancelingQueue) {
            directiveRouter.getDirectiveHandler(directive)?.cancelDirective(directive.getMessageId())
            listeners.forEach {
                it.onCanceled(directive)
            }
        }

        return true
    }

    private fun handleQueuedDirectives(): Boolean {
        lock.lock()

        Logger.d(TAG, "[handleQueuedDirectivesLocked] handlingQueue size : ${handlingQueue.size}")
        if (handlingQueue.isEmpty()) {
            lock.unlock()
            return false
        }

        var handleDirectiveCalled = false

        while (handlingQueue.isNotEmpty()) {
            val it = getNextUnblockedDirectiveLocked()

            if (it == null) {
                Logger.d(TAG, "[handleQueuedDirectivesLocked] all queued directives are blocked ${blockingDirectiveStore.get()}")
                break
            }

            val directive = it.directive
            val policy = it.policy

            blockingDirectiveStore.setDirectiveBeingHandledLocked(directive, policy)
            handlingQueue.remove(it)

            handleDirectiveCalled = true
            lock.unlock()

            listeners.forEach {
                it.onRequested(directive)
            }
            val handleDirectiveSucceeded = directiveRouter.getDirectiveHandler(directive)?.handleDirective(directive.getMessageId()) ?: false
            if (!handleDirectiveSucceeded) {
                listeners.forEach {
                    it.onFailed(directive, "no handler for directive")
                }
            }

            lock.lock()

            // if handle failed or directive is not blocking
            if (!handleDirectiveSucceeded || (policy.blocking == null || policy.blocking?.isEmpty() == true)) {
                blockingDirectiveStore.clearDirectiveBeingHandledLocked(directive, policy)
            }

            if (!handleDirectiveSucceeded) {
                Logger.e(
                    TAG,
                    "[handleQueuedDirectivesLocked] handleDirectiveFailed message id : ${directive.getMessageId()}"
                )
                scrub(directive.getDialogRequestId())
            }
        }

        lock.unlock()
        return handleDirectiveCalled
    }

    private fun getNextUnblockedDirectiveLocked(): DirectiveAndPolicy? {
        // A medium is considered blocked if a previous blocking directive hasn't been completed yet.
        val blockedMediumsMap: MutableMap<String, MutableSet<BlockingPolicy.Medium>> = HashMap<String, MutableSet<BlockingPolicy.Medium>>().apply {

            // Mark mediums used by blocking directives being handled as blocked.
            putAll(blockingDirectiveStore.get().map {
                it.key to it.value.keys
            }.associate {
                it.first to EnumSet.copyOf(it.second)
            })

            // Mark mediums used by blocker
            directiveProcessBlocker.forEach {
                val map = remove(it.dialogRequestId) ?: HashSet()
                map.addAll(it.blockingPolicy)
                put(it.dialogRequestId, map)
            }
        }

        Logger.d(TAG, "[getNextUnblockedDirectiveLocked] block mediums : $blockedMediumsMap")

        for (directiveAndPolicy in handlingQueue) {
            val directive = directiveAndPolicy.directive

            val blockedMediums = blockedMediumsMap[getBlockingKey(directive)]
                ?: return directiveAndPolicy

            val policy = directiveAndPolicy.policy

            val blocked = policy.blockedBy?.any {
                blockedMediums.contains(it)
            } ?: false

            policy.blocking?.forEach {
                blockedMediums.add(it)
            }

            if (!blocked) {
                return directiveAndPolicy
            }
        }

        return null
    }
}
