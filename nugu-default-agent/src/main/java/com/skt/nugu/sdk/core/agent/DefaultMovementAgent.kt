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
package com.skt.nugu.sdk.core.agent

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.skt.nugu.sdk.core.interfaces.capability.movement.AbstractMovementAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.capability.movement.MovementController
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextRequester
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.network.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import java.util.concurrent.Executors

class DefaultMovementAgent(
    contextManager: ContextManagerInterface,
    messageSender: MessageSender,
    movementController: MovementController
) : AbstractMovementAgent(contextManager, messageSender, movementController) {
    companion object {
        private const val TAG = "MovementAgent"

        private const val KEY_PLAY_SERVICE_ID = "playServiceId"

        private const val NAME_MOVE = "Move"
        private const val NAME_ROTATE = "Rotate"
        private const val NAME_DANCE = "Dance"
        private const val NAME_STOP = "Stop"

        private const val NAME_MOVE_STARTED = "MoveStarted"
        private const val NAME_MOVE_FINISHED = "MoveFinished"
        private const val NAME_ROTATE_STARTED = "RotateStarted"
        private const val NAME_ROTATE_FINISHED = "RotateFinished"
        private const val NAME_DANCE_STARTED = "DanceStarted"
        private const val NAME_DANCE_FINISHED = "DanceFinished"
        private const val NAME_EXCEPTION_ENCOUNTERED = "ExceptionEncountered"

        private val MOVE = NamespaceAndName(
            NAMESPACE,
            NAME_MOVE
        )
        private val ROTATE = NamespaceAndName(
            NAMESPACE,
            NAME_ROTATE
        )
        private val DANCE = NamespaceAndName(
            NAMESPACE,
            NAME_DANCE
        )
        private val STOP = NamespaceAndName(
            NAMESPACE,
            NAME_STOP
        )

        private val gson = Gson()
    }

    private val executor = Executors.newSingleThreadExecutor()

    private var currentState =
        State.STOP
    private var currentDirection =
        Direction.NONE

    override val namespaceAndName: NamespaceAndName =
        NamespaceAndName("supportedInterfaces", NAMESPACE)

    init {
        contextManager.setStateProvider(namespaceAndName, this)
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            contextSetter.setState(namespaceAndName, JsonObject().apply {
                addProperty("version", VERSION)
                addProperty("state", currentState.toString())
                addProperty("direction", currentDirection.toString())
            }.toString(), StateRefreshPolicy.ALWAYS, stateRequestToken)
        }
    }

    override fun preHandleDirective(info: DirectiveInfo) {

    }

    override fun handleDirective(info: DirectiveInfo) {
        when (info.directive.getName()) {
            NAME_MOVE -> handleMoveDirective(info)
            NAME_ROTATE -> handleRotateDirective(info)
            NAME_DANCE -> handleDanceDirective(info)
            NAME_STOP -> handleStopDirective(info)
        }
    }

    private fun handleMoveDirective(info: DirectiveInfo) {
        val movePayload = try {
            gson.fromJson(info.directive.payload, MovePayload::class.java)
        } catch (th: Throwable) {
            Logger.e(TAG, "[handleMoveDirective] failed to parse payload", th)
            return
        }

        executor.submit {
            with(movePayload.properties) {
                setContext(State.MOVING, direction)
                if (!movementController.move(
                        direction.toString(),
                        timeInSec,
                        speed,
                        distanceInCm,
                        count,
                        object : MovementController.OnMoveListener {
                            override fun onMoveStarted() {
                                sendEvent(
                                    movePayload.playServiceId,
                                    NAME_MOVE_STARTED
                                )
                            }

                            override fun onMoveFinished() {
                                sendEvent(
                                    movePayload.playServiceId,
                                    NAME_MOVE_FINISHED
                                )
                            }
                        }
                    )
                ) {
                    sendEvent(
                        movePayload.playServiceId,
                        NAME_EXCEPTION_ENCOUNTERED
                    )
                }
            }
        }
    }

    private fun handleRotateDirective(info: DirectiveInfo) {
        fromJsonOrNull(
            info.directive.payload,
            RotatePayload::class.java
        )?.let { rotatePayload ->
            executor.submit {
                with(rotatePayload.properties) {
                    setContext(State.ROTATING, direction)
                    if (!movementController.rotate(
                            direction.toString(),
                            timeInSec,
                            speed,
                            angleInDegree,
                            count,
                            object : MovementController.OnRotateListener {
                                override fun onRotateStarted() {
                                    sendEvent(
                                        rotatePayload.playServiceId,
                                        NAME_ROTATE_STARTED
                                    )
                                }

                                override fun onRotateFinished() {
                                    sendEvent(
                                        rotatePayload.playServiceId,
                                        NAME_ROTATE_FINISHED
                                    )
                                }
                            }
                        )
                    ) {
                        sendEvent(
                            rotatePayload.playServiceId,
                            NAME_EXCEPTION_ENCOUNTERED
                        )
                    }
                }
            }
        }
    }

    private fun handleDanceDirective(info: DirectiveInfo) {
        fromJsonOrNull(info.directive.payload, DancePayload::class.java)?.let { dancePayload ->
            executor.submit {
                setContext(
                    State.DANCING,
                    Direction.NONE
                )
                with(dancePayload.properties) {
                    if (!movementController.dance(
                            count,
                            object : MovementController.OnDanceListener {
                                override fun onDanceStarted() {
                                    sendEvent(
                                        dancePayload.playServiceId,
                                        NAME_DANCE_STARTED
                                    )
                                }

                                override fun onDanceFinished() {
                                    sendEvent(
                                        dancePayload.playServiceId,
                                        NAME_DANCE_FINISHED
                                    )
                                }
                            })
                    ) {
                        sendEvent(
                            dancePayload.playServiceId,
                            NAME_EXCEPTION_ENCOUNTERED
                        )
                    }
                }
            }
        }
    }

    private fun handleStopDirective(info: DirectiveInfo) {
        fromJsonOrNull(info.directive.payload, StopPayload::class.java)?.let {
            executor.submit {
                setContext(
                    State.STOP,
                    Direction.NONE
                )

                if (!movementController.stop()) {
                    sendEvent(
                        it.playServiceId,
                        NAME_EXCEPTION_ENCOUNTERED
                    )
                }
            }
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info)
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val nonBlockingPolicy = BlockingPolicy()

        val configuration = HashMap<NamespaceAndName, BlockingPolicy>()

        configuration[MOVE] = nonBlockingPolicy
        configuration[ROTATE] = nonBlockingPolicy
        configuration[DANCE] = nonBlockingPolicy
        configuration[STOP] = nonBlockingPolicy

        return configuration
    }

    private fun removeDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    private fun sendEvent(playServiceId: String, name: String) {
        contextManager.getContext(object : ContextRequester {
            override fun onContextAvailable(jsonContext: String) {
                messageSender.sendMessage(
                    EventMessageRequest.Builder(jsonContext, NAMESPACE, name, VERSION).payload(
                        JsonObject().apply {
                            addProperty(KEY_PLAY_SERVICE_ID, playServiceId)
                        }.toString()
                    ).build()
                )
            }

            override fun onContextFailure(error: ContextRequester.ContextRequestError) {

            }

        })
    }

    private fun setContext(state: State, direction: Direction) {
        currentState = state
        currentDirection = direction
    }

    private inline fun <reified T> fromJsonOrNull(json: String, javaClass: Class<T>): T? {
        return try {
            gson.fromJson(json, javaClass)
        } catch (th: Throwable) {
            null
        }
    }

    enum class State {
        @SerializedName("MOVING")
        MOVING,
        @SerializedName("ROTATING")
        ROTATING,
        @SerializedName("DANCING")
        DANCING,
        @SerializedName("STOP")
        STOP
    }

    enum class Direction {
        @SerializedName("FRONT")
        FRONT,
        @SerializedName("BACK")
        BACK,
        @SerializedName("LEFT")
        LEFT,
        @SerializedName("RIGHT")
        RIGHT,
        @SerializedName("NONE")
        NONE
    }

    data class MovePayload(
        @SerializedName("playServiceId") val playServiceId: String,
        @SerializedName("properties") val properties: MoveProperty
    )

    data class MoveProperty(
        @SerializedName("direction") val direction: Direction,
        @SerializedName("timeInSec") val timeInSec: Long?,
        @SerializedName("speed") val speed: Long?,
        @SerializedName("distanceInCm") val distanceInCm: Long?,
        @SerializedName("count") val count: Long?
    )

    data class RotatePayload(
        @SerializedName("playServiceId") val playServiceId: String,
        @SerializedName("properties") val properties: RotateProperty
    )

    data class RotateProperty(
        @SerializedName("direction") val direction: Direction,
        @SerializedName("timeInSec") val timeInSec: Long?,
        @SerializedName("speed") val speed: Long?,
        @SerializedName("angleInDegree") val angleInDegree: Long?,
        @SerializedName("count") val count: Long?
    )

    data class DancePayload(
        @SerializedName("playServiceId") val playServiceId: String,
        @SerializedName("properties") val properties: DanceProperty
    )

    data class DanceProperty(@SerializedName("count") val count: Long)

    data class StopPayload(@SerializedName("playServiceId") val playServiceId: String)
}