/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.routine

import com.skt.nugu.sdk.agent.routine.handler.StartDirectiveHandler

interface RoutineAgentInterface {
    enum class State {
        IDLE, PLAYING, STOPPED, FINISHED, INTERRUPTED
    }

    interface RoutineListener {
        fun onPlaying(directive: StartDirectiveHandler.StartDirective)
        fun onStopped(directive: StartDirectiveHandler.StartDirective)
        fun onFinished(directive: StartDirectiveHandler.StartDirective)
        fun onInterrupted(directive: StartDirectiveHandler.StartDirective)
    }

    /**
     * Add listener for routine
     */
    fun addListener(listener: RoutineListener)

    /**
     * Remove listener for routine
     */
    fun removeListener(listener: RoutineListener)

    /**
     * Resume a routine started by [directive]
     */
    fun resume(directive: StartDirectiveHandler.StartDirective): Boolean
    /**
     * Pause a routine started by [directive]
     */
    fun pause(directive: StartDirectiveHandler.StartDirective): Boolean
    /**
     * Stop a routine started by [directive]
     */
    fun stop(directive: StartDirectiveHandler.StartDirective): Boolean
}