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

import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandler
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.LoopThread
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.HashMap
import kotlin.concurrent.withLock

class DirectiveSequencer :
    DirectiveSequencerInterface {
    companion object {
        private const val TAG = "DirectiveSequencer"
    }

    private val directiveRouter = DirectiveRouter()
    private val directiveProcessors = HashMap<String, DirectiveProcessor>()

    private val receivingQueue: Deque<List<Directive>> = ArrayDeque()

    private val receivingThread: LoopThread = object : LoopThread() {
        override fun onLoop() {
            while(true) {
                if(!receiveDirectives()){
                    lock.withLock {
                        if(receivingQueue.isEmpty()) {
                            return
                        }
                    }
                }
            }
        }
    }

    private var isEnabled = true
    private val lock = ReentrantLock()
    private var listeners = CopyOnWriteArraySet<DirectiveSequencerInterface.OnDirectiveHandlingListener>()
    private val processorRemoveExecutor = ScheduledThreadPoolExecutor(1)

    init {
        Logger.d(TAG, "[init]")
        receivingThread.start()
    }

    private fun receiveDirectives(): Boolean {
        Logger.d(TAG, "[receiveDirectives]")
        val directives = lock.withLock {
            if (receivingQueue.isEmpty()) {
                return false
            }
            receivingQueue.pop()
        }

        if(directives.isEmpty()) {
            return false
        }

        val key = directives.first().getDialogRequestId()
        var processor = directiveProcessors[key]
        if(processor == null) {
            Logger.d(TAG, "[receiveDirectives] create directive processor : $key")
            processor = DirectiveProcessor(directiveRouter, object: DirectiveSequencerInterface.OnDirectiveHandlingListener {
                override fun onRequested(directive: Directive) {
                    listeners.forEach {
                        it.onRequested(directive)
                    }
                }

                override fun onCompleted(directive: Directive) {
                    listeners.forEach {
                        it.onCompleted(directive)
                    }
                    processor?.let {
                        processorRemoveExecutor.schedule(
                            { tryRemoveDirectiveProcessor(it, key) },
                            10,
                            TimeUnit.SECONDS
                        )
                    }
                }

                override fun onCanceled(directive: Directive) {
                    listeners.forEach {
                        it.onCanceled(directive)
                    }
                    processor?.let {
                        processorRemoveExecutor.schedule(
                            { tryRemoveDirectiveProcessor(it, key) },
                            10,
                            TimeUnit.SECONDS
                        )
                    }
                }

                override fun onFailed(directive: Directive, description: String) {
                    listeners.forEach {
                        it.onFailed(directive, description)
                    }
                    processor?.let {
                        processorRemoveExecutor.schedule(
                            { tryRemoveDirectiveProcessor(it, key) },
                            10,
                            TimeUnit.SECONDS
                        )
                    }
                }

                private fun tryRemoveDirectiveProcessor(processor: DirectiveProcessor, dialogRequestId: String) {
                    if(!processor.existDirectiveWillBeHandle()) {
                        Logger.d(TAG, "[receiveDirectives] remove directive processor : $dialogRequestId")
                        processor.shutdown()
                        directiveProcessors.remove(dialogRequestId)
                    }
                }
            })

            directiveProcessors[key] = processor
        }
        processor.onDirectives(directives)

        return true
    }

    override fun addOnDirectiveHandlingListener(listener: DirectiveSequencerInterface.OnDirectiveHandlingListener) {
        listeners.add(listener)
    }

    override fun removeOnDirectiveHandlingListener(listener: DirectiveSequencerInterface.OnDirectiveHandlingListener) {
        listeners.remove(listener)
    }

    override fun addDirectiveHandler(handler: DirectiveHandler): Boolean {
        return directiveRouter.addDirectiveHandler(handler)
    }

    override fun removeDirectiveHandler(handler: DirectiveHandler): Boolean {
        return directiveRouter.removeDirectiveHandler(handler)
    }

    override fun onDirectives(directives: List<Directive>): Boolean {
        lock.withLock {
            if (!isEnabled) {
                Logger.w(TAG, "[onDirective] failed, $TAG was disabled")
                return false
            }

            receivingQueue.offer(directives)
            receivingThread.wakeAll()
            return true
        }
    }

    override fun disable() {
        lock.withLock {
            isEnabled = false
            directiveProcessors.forEach {
                it.value.disable()
            }
            // wake receivingThread to cancel queued directives.
            receivingThread.wakeAll()
        }
    }

    override fun enable() {
        lock.withLock {
            isEnabled = true
            receivingThread.wakeAll()
        }
    }
}