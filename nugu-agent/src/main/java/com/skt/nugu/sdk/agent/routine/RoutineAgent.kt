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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.display.DisplayAgentInterface
import com.skt.nugu.sdk.agent.routine.handler.ContinueDirectiveHandler
import com.skt.nugu.sdk.agent.routine.handler.MoveDirectiveHandler
import com.skt.nugu.sdk.agent.routine.handler.StartDirectiveHandler
import com.skt.nugu.sdk.agent.routine.handler.StopDirectiveHandler
import com.skt.nugu.sdk.agent.text.TextAgentInterface
import com.skt.nugu.sdk.agent.text.TextInputRequester
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveProcessorInterface
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.focus.SeamlessFocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

class RoutineAgent(
    private val messageSender: MessageSender,
    private val contextManager: ContextManagerInterface,
    directiveProcessor: DirectiveProcessorInterface,
    private val directiveSequencer: DirectiveSequencerInterface,
    private val directiveGroupProcessor: DirectiveGroupProcessorInterface,
    private val seamlessFocusManager: SeamlessFocusManagerInterface,
    startDirectiveHandleController: StartDirectiveHandler.HandleController? = null,
    continueDirectiveHandleController: ContinueDirectiveHandler.HandleController? = null,
    internal var controller: RoutineAgentInterface.RoutineController? = null,
    private val interruptTimeoutInSecond: Long? = 60
) : CapabilityAgent,
    RoutineAgentInterface,
    SupportedInterfaceContextProvider,
    StartDirectiveHandler.Controller,
    StopDirectiveHandler.Controller,
    ContinueDirectiveHandler.Controller,
    MoveDirectiveHandler.Controller,
    DisplayAgentInterface.Listener {
    companion object {
        private const val TAG = "RoutineAgent"

        val VERSION = Version(1, 3)
        const val NAMESPACE = "Routine"
        const val EVENT_FAILED = "Failed"
    }

    internal data class StateContext(
        val context: RoutineAgentInterface.Context
    ) : BaseContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()

            internal val CompactContextState = object : BaseContextState {
                override fun value(): String = COMPACT_STATE
            }
        }

        override fun value(): String = buildCompactContext().apply {
            context.token?.let {
                addProperty("token", it)
            }
            context.name?.let {
                addProperty("name", it)
            }
            context.routineId?.let {
                addProperty("routineId", it)
            }
            context.routineType?.let {
                addProperty("routineType", it)
            }
            context.routineListType?.let {
                addProperty("routineListType", it)
            }
            addProperty("routineActivity", context.routineActivity.name)
            context.currentAction?.let {
                addProperty("currentAction", it)
            }
            context.actions?.let {
                add("actions", JsonArray().apply {
                    it.forEach {
                        add(it.toJsonObject())
                    }
                })
            }
        }.toString()
    }

    interface OnRoutineRequestCompleteListener {
        fun onCancel()
        fun onFinish()
    }

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val listeners = CopyOnWriteArraySet<RoutineAgentInterface.RoutineListener>()
    private val routineActionResponseDirectiveCanceler: RoutineActionResponseDirectiveCanceler =
        RoutineActionResponseDirectiveCanceler(directiveProcessor)
    private var state = RoutineAgentInterface.State.IDLE
    private var currentRoutineRequest: RoutineRequest? = null
    private var textInputRequests = HashSet<String>()
    private var causingPauseRequests = HashMap<String, EventMessageRequest>()

    var textAgent: TextAgentInterface? = null

    override val namespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, NAMESPACE)

    init {
        directiveGroupProcessor.addDirectiveGroupPreprocessor(routineActionResponseDirectiveCanceler)

        directiveSequencer.addDirectiveHandler(StartDirectiveHandler(this, startDirectiveHandleController))
        directiveSequencer.addDirectiveHandler(StopDirectiveHandler(this))
        directiveSequencer.addDirectiveHandler(ContinueDirectiveHandler(this, continueDirectiveHandleController))
        directiveSequencer.addDirectiveHandler(MoveDirectiveHandler(contextManager, messageSender, this, namespaceAndName))

        contextManager.setStateProvider(namespaceAndName, this)
        messageSender.addOnSendMessageListener(object : MessageSender.OnSendMessageListener {
            override fun onPreSendMessage(request: MessageRequest) {
                if (request is EventMessageRequest) {
                    if (textInputRequests.remove(request.dialogRequestId)) {
                        // if my text request, skip pause.
                        return
                    }

                    currentRoutineRequest?.let {
                        if (shouldPauseRoutine(request)) {
                            causingPauseRequests[request.dialogRequestId] = request
                            Logger.d(TAG, "[onPreSendMessage] pause routine by: $request")
                            // If the request is caused by the current action, do not cancel current action.
                            it.pause(request.referrerDialogRequestId != it.currentActionDialogRequestId)
                        }
                    }
                }
            }

            override fun onPostSendMessage(request: MessageRequest, status: Status) {
                if (!status.isOk() && request is EventMessageRequest) {
                    if (causingPauseRequests.remove(request.dialogRequestId) != null) {
                        currentRoutineRequest?.let {
                            Logger.d(
                                TAG,
                                "[onPostSendMessage] cancel current request (causing request: $request, status: $status)"
                            )
                            it.cancel()
                        }
                    }
                }
            }

            private fun shouldPauseRoutine(request: EventMessageRequest): Boolean {
                return ((request.namespace == "Text" && request.name == "TextInput") || (request.namespace == "Display" && request.name == "ElementSelected")) ||
                        ((request.namespace == "ASR" && request.name == "Recognize") && state != RoutineAgentInterface.State.SUSPENDED)
            }
        })

        directiveSequencer.addOnDirectiveHandlingListener(object :
            DirectiveSequencerInterface.OnDirectiveHandlingListener {
            override fun onRequested(directive: Directive) {
            }

            override fun onCompleted(directive: Directive) {
                if (directive.getNamespace() == "ASR" && directive.getName() == "ExpectSpeech") {
                    currentRoutineRequest?.let {
                        Logger.d(TAG, "pause routine by ASR.ExpectSpeech directive")
                        // If the request is caused by the current action, do not cancel current action.
                        it.pause(directive.getDialogRequestId() != currentRoutineRequest?.currentActionDialogRequestId)
                    }
                }
            }

            override fun onCanceled(directive: Directive) {
            }

            override fun onFailed(directive: Directive, description: String) {
            }
        })

        directiveGroupProcessor.addListener(object : DirectiveGroupProcessorInterface.Listener {
            override fun onPostProcessed(directives: List<Directive>) {
                directives.firstOrNull()?.getDialogRequestId()?.let { dialogRequestId ->
                    if (causingPauseRequests.remove(dialogRequestId) != null) {
//                        if (!directives.any { it.getNamespaceAndName() == ContinueDirectiveHandler.CONTINUE || (it.getNamespace() == "ASR" && it.getName() == "NotifyResult") }) {
//                            currentRoutineRequest?.cancel()
//                        }
                    }
                }
            }
        })
    }

    data class ActionTimeoutFuture(
        val dialogRequestId: String,
        val future: ScheduledFuture<*>,
        var runOnTimeout: Runnable? = null
    )

    private inner class RoutineRequest(
        val directive: StartDirectiveHandler.StartDirective,
        private val listener: OnRoutineRequestCompleteListener
    ) : SeamlessFocusManagerInterface.Requester {
        private var currentActionIndex: Int = 0
        private var currentActionHandlingListener: DirectiveGroupHandlingListener? = null
        var currentActionDialogRequestId: String? = null
        private var isCancelRequested = false
        private var scheduledFutureForTryStartNextAction: ScheduledFuture<*>? = null
        private var scheduledFutureForCancelByInterrupt: ScheduledFuture<*>? = null
        private var scheduledFutureForActionTimeout: ActionTimeoutFuture? = null
        private val isActionRequesting = AtomicBoolean(false)

        fun start() {
            Logger.d(TAG, "[RoutineRequest] start")
            directive.payload.actions.firstOrNull()?.let {
                doAction(it)
            }
        }

        fun cancel(cancelCurrentAction: Boolean = true, cancelScheduledTimeoutFuture: Boolean = true) {
            Logger.d(
                TAG,
                "[RoutineRequest] isCancelRequested: $isCancelRequested, cancelCurrentAction: $cancelCurrentAction"
            )
            if (isCancelRequested) {
                return
            }
            isCancelRequested = true
            if (cancelCurrentAction) {
                cancelCurrentAction(cancelScheduledTimeoutFuture)
            }
            listener.onCancel()
        }

        fun pause(cancelCurrentAction: Boolean = true) {
            if(state == RoutineAgentInterface.State.INTERRUPTED) {
                Logger.d(TAG, "[RoutineRequest] pause() ignored by state: $state")
                return
            }

            if(isCancelRequested) {
                Logger.d(TAG, "[RoutineRequest] pause() ignored by isCanceled: $isCancelRequested")
                return
            }

            Logger.d(
                TAG,
                "[RoutineRequest] pause - cancelCurrentAction: $cancelCurrentAction"
            )
            if (cancelCurrentAction) {
                cancelCurrentAction()
            }
            cancelNextScheduledAction()

            interruptTimeoutInSecond?.let {
                scheduledFutureForCancelByInterrupt?.cancel(true)
                scheduledFutureForCancelByInterrupt = executor.schedule({
                    cancel(cancelCurrentAction)
                }, it, TimeUnit.SECONDS)
            }

            setState(RoutineAgentInterface.State.INTERRUPTED, directive)
        }

        fun move(position: Long): Boolean {
            if(isActionRequesting.get()) {
                return false
            }

            val targetIndex = (position - 1).toInt()

            return if(targetIndex != -1) {
                cancelCurrentAction()
                pause()
                scheduledFutureForCancelByInterrupt?.cancel(true)
                scheduledFutureForCancelByInterrupt = null
                tryStartActionIndexAt(targetIndex)
                true
            } else {
                false
            }
        }

        private fun cancelNextScheduledAction() {
            scheduledFutureForTryStartNextAction?.cancel(true)
            scheduledFutureForTryStartNextAction = null
        }

        private fun cancelCurrentAction(cancelScheduledTimeoutFuture: Boolean = true) {
            currentActionDialogRequestId?.let {
                routineActionResponseDirectiveCanceler.requestCancel(it)
            }
            if(cancelScheduledTimeoutFuture) {
                scheduledFutureForActionTimeout?.future?.cancel(true)
            }
        }

        fun cancelIfCurrentRoutineDisplayCleared(dialogRequestId: String) {
            Logger.d(
                TAG,
                "[RoutineRequest] cancelIfCurrentRoutineDisplayCleared: $currentActionDialogRequestId, $dialogRequestId"
            )
            if (currentActionDialogRequestId == dialogRequestId) {
                Logger.d(
                    TAG,
                    "[RoutineRequest] cancelIfCurrentRoutineDisplayCleared: $dialogRequestId"
                )
                cancel()
            }
        }

        fun doActionWithOffset(offset: Int) {
            Logger.d(
                TAG,
                "[RoutineRequest] doActionWithOffset - isCanceled: $isCancelRequested / offset: $offset"
            )
            if (isCancelRequested) {
                return
            }
            cancelNextScheduledAction()
            scheduledFutureForCancelByInterrupt?.cancel(true)
            scheduledFutureForCancelByInterrupt = null

            setState(RoutineAgentInterface.State.PLAYING, directive)
            tryStartActionIndexAt(currentActionIndex + offset)
        }
        
        private fun tryStartNextAction() {
            Logger.d(
                TAG,
                "[RoutineRequest] tryStartNextAction - $currentActionIndex, isCanceled: $isCancelRequested"
            )
            if (isCancelRequested) {
                return
            }

            tryStartActionIndexAt(currentActionIndex + 1)
        }

        private fun tryStartActionIndexAt(index: Int) {
            Logger.d(
                TAG,
                "[RoutineRequest] tryStartActionIndexAt - target index:$index, current index: $currentActionIndex, isCanceled: $isCancelRequested"
            )

            if (isCancelRequested) {
                return
            }

            if (index < directive.payload.actions.size) {
                val currentController = controller

                if(currentController == null || currentController.continueAction(index, directive)) {
                    currentActionIndex = index
                    doAction(directive.payload.actions[currentActionIndex])
                } else {
                    pause()
                }
            } else {
                listener.onFinish()
            }
        }

        fun getCurrentActionIndex(): Int = currentActionIndex

        private fun doAction(action: Action) {
            Logger.d(TAG, "[RoutineRequest] doAction - $action")

            when(action.type) {
                Action.Type.TEXT -> {
                    doTextAction(action)
                }
                Action.Type.DATA -> {
                    doDataAction(action)
                }
                Action.Type.BREAK -> {
                    doBreakAction(action)
                }
            }
        }

        private fun scheduleActionTimeoutTriggeredEvent(
            delay: Long,
            action: Action,
            directive: StartDirectiveHandler.StartDirective,
            dialogRequestId: String
        ): ScheduledFuture<*> = executor.schedule({
            if(currentRoutineRequest?.currentActionDialogRequestId != dialogRequestId) {
                return@schedule
            }

            contextManager.getContext(object : IgnoreErrorContextRequestor() {
                override fun onContext(jsonContext: String) {
                    val request = EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        "ActionTimeoutTriggered",
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", directive.payload.playServiceId)
                        action.token?.let {
                            addProperty("token", it)
                        }
                    }.toString())
                        .referrerDialogRequestId(directive.header.referrerDialogRequestId ?: "")
                        .build()

                    messageSender.newCall(request).enqueue(object: MessageSender.Callback {
                        override fun onFailure(request: MessageRequest, status: Status) {
                        }

                        override fun onSuccess(request: MessageRequest) {
                            if(scheduledFutureForActionTimeout?.dialogRequestId == dialogRequestId) {
                                scheduledFutureForActionTimeout?.runOnTimeout?.run()
                            }
                        }

                        override fun onResponseStart(request: MessageRequest) {
                        }
                    })
                }
            })
        }, delay, TimeUnit.MILLISECONDS)

        private fun createDirectiveGroupHandlingListenerForActionHandling(
            dialogRequestId: String,
            action: Action
        ): DirectiveGroupHandlingListener {
            var applyMuteDelay = false

            val directiveGroupPrepareListener = object : DirectiveGroupHandlingListener.OnDirectiveGroupPrepareListener {
                override fun onPrepared(directives: List<Directive>) {
                    Logger.d(TAG, "[onPrepared] current action index: $currentActionIndex, currentActionDialogRequestId: $currentActionDialogRequestId, preparedDialogRequestId: $dialogRequestId")
                    if(currentActionDialogRequestId != dialogRequestId) {
                        return
                    }

                    if(action.muteDelayInMilliseconds != null && !directives.any { it.header.namespace == "TTS" && it.header.name == "Speak" }) {
                        applyMuteDelay = true
                    }
                    action.actionTimeoutInMilliseconds?.let {
                        setSuspendedState(directive, currentActionIndex, System.currentTimeMillis() + it)
                        scheduledFutureForActionTimeout = ActionTimeoutFuture(dialogRequestId, scheduleActionTimeoutTriggeredEvent(it, action, directive, dialogRequestId))
                    }
                    listeners.forEach {
                        it.onActionStarted(currentActionIndex, directive, directives)
                    }
                }
            }

            return DirectiveGroupHandlingListener(
                dialogRequestId,
                directiveGroupProcessor,
                directiveSequencer,
                object : DirectiveGroupHandlingListener.OnDirectiveResultListener {
                    private fun onFinishInternal(results: Map<Directive, DirectiveGroupHandlingListener.Result>, tryStartNextAction: Boolean = true) {
                        if(currentActionDialogRequestId != dialogRequestId) {
                            return
                        }

                        Logger.d(TAG, "[onFinishInternal] handling dialogRequestId: $dialogRequestId, current action dialogRequestId: $currentActionDialogRequestId")

                        listeners.forEach {
                            it.onActionFinished(currentActionIndex, directive, results)
                        }

                        if (results.values.any { it == DirectiveGroupHandlingListener.Result.CANCELED || it == DirectiveGroupHandlingListener.Result.FAILED }) {
                            pause()
                        } else {
                            cancelNextScheduledAction()

                            if(!tryStartNextAction && currentActionIndex < directive.payload.actions.size - 1) {
                                return
                            }

                            val delay =
                                if (action.muteDelayInMilliseconds != null && applyMuteDelay) {
                                    action.muteDelayInMilliseconds
                                } else action.postDelayInMilliseconds

                            if (delay != null) {
                                scheduledFutureForTryStartNextAction =
                                    executor.schedule(
                                        {
                                            tryStartNextAction()
                                        },
                                        delay,
                                        TimeUnit.MILLISECONDS
                                    )
                            } else {
                                tryStartNextAction()
                            }
                        }
                    }

                    override fun onFinish(results: Map<Directive, DirectiveGroupHandlingListener.Result>) {
                        if(scheduledFutureForActionTimeout?.dialogRequestId == dialogRequestId) {
                            // will be handled after action timeout.
                            scheduledFutureForActionTimeout?.runOnTimeout = Runnable {
                                onFinishInternal(results, false)
                            }
                        } else {
                            onFinishInternal(results)
                        }
                    }

                    override fun onCompleted(directive: Directive) {
                        if(currentActionDialogRequestId != dialogRequestId) {
                            return
                        }

                        listeners.forEach {
                            it.onActionItemFinished(currentActionIndex, directive, DirectiveGroupHandlingListener.Result.COMPLETE)
                        }
                    }

                    override fun onCanceled(directive: Directive) {
                        if(currentActionDialogRequestId != dialogRequestId) {
                            return
                        }
                        pause()
                        listeners.forEach {
                            it.onActionItemFinished(currentActionIndex, directive, DirectiveGroupHandlingListener.Result.CANCELED)
                        }
                    }

                    override fun onFailed(directive: Directive) {
                        if(currentActionDialogRequestId != dialogRequestId) {
                            return
                        }
                        pause()
                        listeners.forEach {
                            it.onActionItemFinished(currentActionIndex, directive, DirectiveGroupHandlingListener.Result.FAILED)
                        }
                    }

                    override fun onSkipped(directive: Directive) {
                        if(currentActionDialogRequestId != dialogRequestId) {
                            return
                        }
                        listeners.forEach {
                            it.onActionItemFinished(currentActionIndex, directive, DirectiveGroupHandlingListener.Result.SKIPPED)
                        }
                    }
                },
                directiveGroupPrepareListener
            )
        }

        private fun doTextAction(action: Action) {
            val text = action.text
            if(text == null) {
                Logger.w(TAG, "[doTextAction] text should not be null")
                return
            }

            if(!isActionRequesting.compareAndSet(false, true)) {
                return
            }

            setState(RoutineAgentInterface.State.PLAYING, directive)
            textAgent?.textInput(
                TextInputRequester.Request.Builder(text)
                    .playServiceId(action.playServiceId).token(action.token)
                    .source("DYNAMIC_ROUTINE_ACTION")
                    .includeDialogAttribute(false), object : TextAgentInterface.RequestListener {
                    override fun onRequestCreated(dialogRequestId: String) {
                        Logger.d(TAG, "[onRequestCreated] dialogRequestId: $dialogRequestId")
                        textInputRequests.add(dialogRequestId)
                        currentActionDialogRequestId = dialogRequestId
                        currentActionHandlingListener = createDirectiveGroupHandlingListenerForActionHandling(dialogRequestId, action)
                        isActionRequesting.set(false)
                    }

                    override fun onReceiveResponse(dialogRequestId: String) {
                    }

                    override fun onError(
                        dialogRequestId: String,
                        type: TextAgentInterface.ErrorType
                    ) {
                        currentActionDialogRequestId = null
                        currentActionHandlingListener = null
                        pause(directive)
                        isActionRequesting.set(false)
                    }
                })
        }

        private fun doDataAction(action: Action) {
            val data = action.data
            if(data == null) {
                Logger.w(TAG, "[doDataAction] data should not be null")
                return
            }

            if(!isActionRequesting.compareAndSet(false, true)) {
                return
            }

            setState(RoutineAgentInterface.State.PLAYING, directive)
            contextManager.getContext(object : IgnoreErrorContextRequestor() {
                override fun onContext(jsonContext: String) {
                    val request = EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        "ActionTriggered",
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        action.playServiceId?.let {
                            addProperty("playServiceId", it)
                        }
                        add("data", action.data)
                    }.toString())
                        .referrerDialogRequestId(directive.header.referrerDialogRequestId ?: "")
                        .build()

                    Logger.d(
                        TAG,
                        "doDataAction - [onContext] dialogRequestId: ${request.dialogRequestId}"
                    )

                    currentActionDialogRequestId = request.dialogRequestId
                    currentActionHandlingListener = createDirectiveGroupHandlingListenerForActionHandling(request.dialogRequestId, action)

                    messageSender.newCall(request).enqueue(object : MessageSender.Callback {
                        override fun onFailure(request: MessageRequest, status: Status) {
                            currentActionDialogRequestId = null
                            currentActionHandlingListener = null
                            pause(directive)
                            isActionRequesting.set(false)
                        }

                        override fun onSuccess(request: MessageRequest) {
                        }

                        override fun onResponseStart(request: MessageRequest) {
                            isActionRequesting.set(false)
                        }
                    })
                }
            })
        }

        private fun doBreakAction(action: Action) {
            val muteDelayInMilliseconds = action.muteDelayInMilliseconds
            if(muteDelayInMilliseconds == null) {
                Logger.w(TAG, "[doBreakAction] muteDelayInMilliseconds should not be null")
                return
            }

            if(!isActionRequesting.compareAndSet(false, true)) {
                return
            }
            setSuspendedState(directive, currentActionIndex, System.currentTimeMillis() + muteDelayInMilliseconds)
            // mute 시간 이후, 다음 action을 실행하도록 함.
            scheduledFutureForTryStartNextAction?.cancel(true)
            scheduledFutureForTryStartNextAction =
                executor.schedule(
                    {
                        tryStartNextAction()
                    },
                    muteDelayInMilliseconds,
                    TimeUnit.MILLISECONDS
                )

            isActionRequesting.set(false)
        }
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        contextType: ContextType,
        stateRequestToken: Int
    ) {
        Logger.d(
            TAG,
            "[provideState] namespaceAndName: $namespaceAndName, contextType: $contextType, stateRequestToken: $stateRequestToken"
        )

        contextSetter.setState(
            namespaceAndName,
            StateContext(getContext()),
            StateRefreshPolicy.ALWAYS,
            contextType,
            stateRequestToken
        )
    }

    override fun start(directive: StartDirectiveHandler.StartDirective): Boolean {
        Logger.d(TAG, "[start] $directive")
        val request = currentRoutineRequest
        if (request != null) {
            Logger.d(TAG, "[start] already started routine exist, so try cancel it.")
            request.cancel()
        }

        val onRoutineCompleteListener = object : OnRoutineRequestCompleteListener {
            override fun onCancel() {
                Logger.d(TAG, "[start] onCancel() dialogRequestId: ${directive.header.dialogRequestId}")
                setState(RoutineAgentInterface.State.STOPPED, directive)
                currentRoutineRequest?.let {
                    seamlessFocusManager.cancel(it)
                }
                currentRoutineRequest = null
                sendRoutineStopEvent(directive)
            }

            override fun onFinish() {
                Logger.d(TAG, "[start] onFinish() dialogRequestId: ${directive.header.dialogRequestId}")
                setState(RoutineAgentInterface.State.FINISHED, directive)
                currentRoutineRequest?.let {
                    seamlessFocusManager.cancel(it)
                }
                currentRoutineRequest = null
                sendRoutineFinishEvent(directive)
            }
        }

        textAgent?.let {
            val countDownLatch = CountDownLatch(1)
            RoutineRequest(directive, onRoutineCompleteListener).apply {
                currentRoutineRequest = this

                setState(RoutineAgentInterface.State.PLAYING, directive)

                contextManager.getContext(object : IgnoreErrorContextRequestor() {
                    override fun onContext(jsonContext: String) {
                        messageSender.newCall(
                            EventMessageRequest.Builder(
                                jsonContext,
                                NAMESPACE,
                                "Started",
                                VERSION.toString()
                            ).payload(JsonObject().apply {
                                addProperty("playServiceId", directive.payload.playServiceId)
                            }.toString())
                                .referrerDialogRequestId(directive.header.dialogRequestId)
                                .build()
                        ).enqueue(object : MessageSender.Callback {
                            override fun onFailure(request: MessageRequest, status: Status) {
                                countDownLatch.countDown()
                                setState(RoutineAgentInterface.State.STOPPED, directive)
                                currentRoutineRequest = null
                            }

                            override fun onSuccess(request: MessageRequest) {
                                countDownLatch.countDown()
                            }

                            override fun onResponseStart(request: MessageRequest) {
                                countDownLatch.countDown()
                                currentRoutineRequest?.let {
                                    seamlessFocusManager.prepare(it)
                                }
                                start()
                            }
                        })
                    }
                }, namespaceAndName)

                countDownLatch.await()
            }
        }

        return currentRoutineRequest != null
    }

    override fun failed(directive: StartDirectiveHandler.StartDirective, errorMessage: String) {
        Logger.d(TAG, "[failed] $directive")
        sendRoutineFailedEvent(directive.payload.playServiceId, errorMessage, directive.header.dialogRequestId)
    }

    private fun sendRoutineFailedEvent(playServiceId: String, errorMessage: String, referrerDialogRequestId: String) {
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        EVENT_FAILED,
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", playServiceId)
                        addProperty("errorMessage", errorMessage)
                    }.toString())
                        .referrerDialogRequestId(referrerDialogRequestId)
                        .build()
                ).enqueue(null)
            }
        }, namespaceAndName)
    }

    private fun sendRoutineStopEvent(directive: StartDirectiveHandler.StartDirective) {
        Logger.d(TAG, "[sendRoutineStopEvent]")
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        "Stopped",
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", directive.payload.playServiceId)
                    }.toString())
                        .referrerDialogRequestId(directive.header.dialogRequestId)
                        .build()
                ).enqueue(null)
            }
        }, namespaceAndName)
    }

    private fun sendRoutineFinishEvent(directive: StartDirectiveHandler.StartDirective) {
        Logger.d(TAG, "[sendRoutineFinishEvent]")
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        "Finished",
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", directive.payload.playServiceId)
                    }.toString())
                        .referrerDialogRequestId(directive.header.dialogRequestId)
                        .build()
                ).enqueue(null)
            }
        }, namespaceAndName)
    }

    override fun stop(directive: StopDirectiveHandler.StopDirective): Boolean {
        Logger.d(TAG, "[stop] $directive")
        return stopInternal(directive.payload.token)
    }

    override fun move(directive: MoveDirectiveHandler.MoveDirective): Boolean {
        Logger.d(TAG, "[move $directive]")
        return currentRoutineRequest?.move(directive.payload.position) ?: false
    }

    override fun doContinue(directive: ContinueDirectiveHandler.ContinueDirective): Boolean {
        Logger.d(TAG, "[doContinue] $directive, $causingPauseRequests")

        // cancel doContinue if paused by another request.
        return if (causingPauseRequests.isEmpty()) {
            resumeInternal(directive.payload.token)
        } else {
            false
        }
    }

    override fun failed(
        directive: ContinueDirectiveHandler.ContinueDirective,
        errorMessage: String
    ) {
        Logger.d(TAG, "[failed] $directive")
        sendRoutineFailedEvent(directive.payload.playServiceId, errorMessage, directive.header.dialogRequestId)
    }

    private fun resumeInternal(token: String): Boolean {
        return commandInternal(token) {
            it.doActionWithOffset(1)
        }
    }

    private fun resumeCurrentInternal(token: String): Boolean {
        return commandInternal(token) {
            it.doActionWithOffset(0)
        }
    }

    private fun pauseInternal(token: String): Boolean {
        return commandInternal(token) {
            it.pause()
        }
    }

    private fun stopInternal(token: String, cancelScheduledTimeoutFuture: Boolean = true): Boolean {
        return commandInternal(token) {
            it.cancel(cancelScheduledTimeoutFuture = cancelScheduledTimeoutFuture)
        }
    }

    private fun commandInternal(
        token: String,
        command: (request: RoutineRequest) -> Unit
    ): Boolean {
        val request = currentRoutineRequest
        if (request != null) {
            if (request.directive.payload.token == token) {
                command(request)
                return true
            }
        }

        return false
    }

    override fun onRendered(templateId: String, dialogRequestId: String) {
    }

    override fun onCleared(templateId: String, dialogRequestId: String, canceled: Boolean) {
        if (canceled) {
            currentRoutineRequest?.cancelIfCurrentRoutineDisplayCleared(dialogRequestId)
        }
    }

    override fun getState(): RoutineAgentInterface.State = state
    override fun getContext(): RoutineAgentInterface.Context {
        val request = currentRoutineRequest
        return if (request == null) {
            RoutineAgentInterface.Context(null, null, state, null, null, null, null, null)
        } else {
            val payload = request.directive.payload
            RoutineAgentInterface.Context(
                payload.token,
                payload.name,
                state,
                request.getCurrentActionIndex() + 1,
                payload.actions,
                payload.routineId,
                payload.routineType,
                payload.routineListType
            )
        }
    }

    override fun addListener(listener: RoutineAgentInterface.RoutineListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: RoutineAgentInterface.RoutineListener) {
        listeners.remove(listener)
    }

    override fun setRoutineController(controller: RoutineAgentInterface.RoutineController?) {
        this.controller = controller
    }

    override fun resume(directive: StartDirectiveHandler.StartDirective): Boolean {
        Logger.d(TAG, "[resume] $directive")
        return resumeInternal(directive.payload.token)
    }

    override fun resumeCurrent(directive: StartDirectiveHandler.StartDirective): Boolean {
        Logger.d(TAG, "[resumeCurrent]")
        return resumeCurrentInternal(directive.payload.token)
    }

    override fun pause(directive: StartDirectiveHandler.StartDirective): Boolean {
        Logger.d(TAG, "[pause] $directive")
        return pauseInternal(directive.payload.token)
    }

    override fun stop(directive: StartDirectiveHandler.StartDirective): Boolean {
        Logger.d(TAG, "[stop] $directive")
        return stopInternal(directive.payload.token)
    }

    override fun stopSoftly(directive: StartDirectiveHandler.StartDirective): Boolean {
        Logger.d(TAG, "[stopSoftly] $directive")
        return stopInternal(directive.payload.token, false)
    }

    override fun next(): Boolean {
        Logger.d(TAG, "[next]")
        currentRoutineRequest?.let {
            sendMoveControlEvent(it, 1)
            return true
        }
        return false
    }

    override fun prev(): Boolean {
        Logger.d(TAG, "[prev]")
        currentRoutineRequest?.let {
            sendMoveControlEvent(it, -1)
            return true
        }
        return false
    }

    private fun sendMoveControlEvent(request: RoutineRequest, offset: Int) {
        contextManager.getContext(object : IgnoreErrorContextRequestor() {
            override fun onContext(jsonContext: String) {
                messageSender.newCall(
                    EventMessageRequest.Builder(
                        jsonContext,
                        NAMESPACE,
                        "MoveControl",
                        VERSION.toString()
                    ).payload(JsonObject().apply {
                        addProperty("playServiceId", request.directive.payload.playServiceId)
                        addProperty("offset", offset)
                    }.toString()).referrerDialogRequestId(request.directive.header.dialogRequestId)
                        .build()
                ).enqueue(null)
            }
        }, namespaceAndName)
    }

    private fun setState(state: RoutineAgentInterface.State, directive: StartDirectiveHandler.StartDirective) {
        Logger.d(TAG, "[setState] from: ${this.state} , to: $state , directive: ${directive.header}")
        if(this.state == state) {
            return
        }

        if(state == RoutineAgentInterface.State.SUSPENDED) {
            Logger.w(TAG, "[setState] call setSuspendedState(...) instead of this!!!")
            return
        }

        this.state = state
        when(state) {
            RoutineAgentInterface.State.PLAYING -> listeners.forEach { it.onPlaying(directive) }
            RoutineAgentInterface.State.STOPPED -> listeners.forEach { it.onStopped(directive) }
            RoutineAgentInterface.State.FINISHED -> listeners.forEach { it.onFinished(directive) }
            RoutineAgentInterface.State.INTERRUPTED -> listeners.forEach { it.onInterrupted(directive) }
            else -> {
                // ignore idle (never called)
            }
        }
    }

    private fun setSuspendedState(directive: StartDirectiveHandler.StartDirective, index: Int, expectedTerminateTimestamp: Long) {
        if(this.state == RoutineAgentInterface.State.SUSPENDED) {
            return
        }

        this.state = RoutineAgentInterface.State.SUSPENDED

        listeners.forEach { it.onSuspended(directive, index, expectedTerminateTimestamp) }
    }
}