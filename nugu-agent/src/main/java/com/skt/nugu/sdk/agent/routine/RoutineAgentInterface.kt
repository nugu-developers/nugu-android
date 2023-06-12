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
import com.skt.nugu.sdk.core.interfaces.message.Directive

interface RoutineAgentInterface {
    enum class State {
        IDLE, PLAYING, STOPPED, FINISHED, INTERRUPTED, SUSPENDED
    }

    interface RoutineController {
        /**
         * Called before do [directive] action index of [actionIndex]
         * @return true: do action, false: do not action and try to pause routine.
         */
        fun continueAction(actionIndex: Int, directive: StartDirectiveHandler.StartDirective): Boolean
    }

    interface RoutineListener {
        fun onPlaying(directive: StartDirectiveHandler.StartDirective)
        fun onStopped(directive: StartDirectiveHandler.StartDirective)
        fun onFinished(directive: StartDirectiveHandler.StartDirective)
        fun onInterrupted(directive: StartDirectiveHandler.StartDirective)
        fun onSuspended(directive: StartDirectiveHandler.StartDirective, index: Int, expectedTerminateTimestamp: Long)

        /**
         * Called a [index] action started of routine for [directive]
         * @param index started [Action] index for [StartDirectiveHandler.StartDirective.Payload.actions] (0-based index)
         * @param directive the directive contain [Action]
         * @param actionItems the directive's for action request
         */
        fun onActionStarted(index: Int, directive: StartDirectiveHandler.StartDirective, actionItems: List<Directive>?)

        /**
         * Called a [index] action finished of routine for [directive]
         * @param index finished [Action] index for [StartDirectiveHandler.StartDirective.Payload.actions] (0-based index)
         * @param directive the directive contain [Action]
         * @param actionItemAndResults the directive's for action request and it's result
         */
        fun onActionFinished(index: Int, directive: StartDirectiveHandler.StartDirective, actionItemAndResults: Map<Directive, DirectiveGroupHandlingListener.Result>?)

        // TODO : If we need onActionItemStarted callback, then impl

        /**
         * Called when a item[item] of action of [actionIndex] is finished with [result]
         * @param actionIndex a index of action
         * @param item a item for action
         * @param result a result of item
         */
        fun onActionItemFinished(actionIndex: Int, item: Directive, result: DirectiveGroupHandlingListener.Result)
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
     * Set routine controller
     * @param controller null to remove controller.
     */
    fun setRoutineController(controller: RoutineController? = null)

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

    /**
     * Request next action
     */
    fun next(): Boolean

    /**
     * Request prev action
     */
    fun prev(): Boolean

    data class Context(
        val token: String?,
        val name: String?,
        val routineActivity: State,
        val currentAction: Int?,
        val actions: Array<Action>?,
        val routineId: String?,
        val routineType: String?,
        val routineListType: String?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Context

            if (token != other.token) return false
            if (name != other.name) return false
            if (routineActivity != other.routineActivity) return false
            if (currentAction != other.currentAction) return false
            if (actions != null) {
                if (other.actions == null) return false
                if (!actions.contentEquals(other.actions)) return false
            } else if (other.actions != null) return false
            if (routineId != other.routineId) return false
            if (routineType != other.routineType) return false
            if (routineListType != other.routineListType) return false

            return true
        }

        override fun hashCode(): Int {
            var result = token?.hashCode() ?: 0
            result = 31 * result + (name?.hashCode() ?: 0)
            result = 31 * result + routineActivity.hashCode()
            result = 31 * result + (currentAction ?: 0)
            result = 31 * result + (actions?.contentHashCode() ?: 0)
            result = 31 * result + (routineId?.hashCode() ?: 0)
            result = 31 * result + (routineType?.hashCode() ?: 0)
            result = 31 * result + (routineListType?.hashCode() ?: 0)
            return result
        }
    }
}