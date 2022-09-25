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
        IDLE, PLAYING, STOPPED, FINISHED, INTERRUPTED, SUSPENDED
    }

    interface RoutineListener {
        fun onPlaying(directive: StartDirectiveHandler.StartDirective)
        fun onStopped(directive: StartDirectiveHandler.StartDirective)
        fun onFinished(directive: StartDirectiveHandler.StartDirective)
        fun onInterrupted(directive: StartDirectiveHandler.StartDirective)
        fun onSuspended(directive: StartDirectiveHandler.StartDirective)

        /**
         * Called a [index] action started of routine for [directive]
         * @param index started [Action] index for [StartDirectiveHandler.StartDirective.Payload.actions] (0-based index)
         * @param directive the directive contain [Action]
         */
        fun onActionStarted(index: Int, directive: StartDirectiveHandler.StartDirective)

        /**
         * Called a [index] action finished of routine for [directive]
         * @param index finished [Action] index for [StartDirectiveHandler.StartDirective.Payload.actions] (0-based index)
         * @param directive the directive contain [Action]
         */
        fun onActionFinished(index: Int, directive: StartDirectiveHandler.StartDirective)
    }

    fun getState(): State

    /**
     * Returns a current context
     */
    fun getContext(): Context

    /**
     * Add listener for routine
     */
    fun addListener(listener: RoutineListener)

    /**
     * Remove listener for routine
     */
    fun removeListener(listener: RoutineListener)

    /**
     * Start a routine
     * @param directive a new directive which to start
     */
    fun start(directive: StartDirectiveHandler.StartDirective): Boolean

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

    data class Context(
        val token: String?,
        val routineActivity: State,
        val currentAction: Int?,
        val actions: Array<Action>?,
        val routineId: String?,
        val routineType: String?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Context

            if (token != other.token) return false
            if (routineActivity != other.routineActivity) return false
            if (currentAction != other.currentAction) return false
            if (actions != null) {
                if (other.actions == null) return false
                if (!actions.contentEquals(other.actions)) return false
            } else if (other.actions != null) return false
            if (routineId != other.routineId) return false
            if (routineType != other.routineType) return false

            return true
        }

        override fun hashCode(): Int {
            var result = token?.hashCode() ?: 0
            result = 31 * result + routineActivity.hashCode()
            result = 31 * result + (currentAction ?: 0)
            result = 31 * result + (actions?.contentHashCode() ?: 0)
            result = 31 * result + (routineId?.hashCode() ?: 0)
            result = 31 * result + (routineType?.hashCode() ?: 0)
            return result
        }

    }
}