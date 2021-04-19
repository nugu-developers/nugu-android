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

import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandler
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.LoopThread
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DirectiveSequencer :
    DirectiveSequencerInterface {
    companion object {
        private const val TAG = "DirectiveSequencer"
    }

    private val directiveRouter = DirectiveRouter()
    private val directiveProcessor: DirectiveProcessor

    private val receivingQueue: Deque<List<Directive>> = ArrayDeque<List<Directive>>()

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

    init {
        Logger.d(TAG, "[init]")
        directiveProcessor = DirectiveProcessor(directiveRouter)
        receivingThread.start()
    }

    private fun receiveDirectives(): Boolean {
        val directives = lock.withLock {
            if (receivingQueue.isEmpty()) {
                return false
            }
            receivingQueue.pop()
        }

        directiveProcessor.onDirectives(directives)
        return true
    }

    override fun addOnDirectiveHandlingListener(listener: DirectiveSequencerInterface.OnDirectiveHandlingListener) {
        directiveProcessor.addOnDirectiveHandlingListener(listener)
    }

    override fun removeOnDirectiveHandlingListener(listener: DirectiveSequencerInterface.OnDirectiveHandlingListener) {
        directiveProcessor.removeOnDirectiveHandlingListener(listener)
    }

    override fun addDirectiveHandler(handler: DirectiveHandler) {
        directiveRouter.addDirectiveHandler(handler)
    }

    override fun removeDirectiveHandler(handler: DirectiveHandler) {
        directiveRouter.removeDirectiveHandler(handler)
    }

    override fun onDirectives(directives: List<Directive>) {
        lock.withLock {
            if (!isEnabled) {
                Logger.w(TAG, "[onDirectives] failed, $TAG was disabled")
                return
            }

            Logger.d(TAG, "[onDirectives] : $directives")
            receivingQueue.offer(directives)
            receivingThread.wakeAll()
        }
    }

    override fun disable() {
        lock.withLock {
            isEnabled = false
            directiveProcessor.disable()
            // wake receivingThread to cancel queued directives.
            receivingThread.wakeAll()
        }
    }

    override fun enable() {
        lock.withLock {
            isEnabled = true
            directiveProcessor.enable()
            receivingThread.wakeAll()
        }
    }

    override fun cancelDialogRequestId(dialogRequestId: String) {
        directiveProcessor.cancelDialogRequestId(dialogRequestId)
    }
}