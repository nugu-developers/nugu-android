package com.skt.nugu.sdk.agent.nudge

import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveHandlerResult
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify

class NudgeDirectiveHandlerTest {
    @get:Rule
    var name: TestName = TestName()

    lateinit var nudgeDirectiveHandler: NudgeDirectiveHandler
    private val directiveHandlerResult = mock(DirectiveHandlerResult::class.java)
    private val nudgeDirectiveObserver = mock(NudgeDirectiveObserver::class.java)

    @Before
    fun setup() {
        nudgeDirectiveHandler = NudgeDirectiveHandler(nudgeDirectiveObserver)
    }

    @Test
    fun test1_handleDirective() {
        val nudgePayload = "{\"nudgeInfo\":{\"key\":\"value\"}}"
        val directive = Directive(null, Header("abc", "123", "", "", "", ""), nudgePayload)

        nudgeDirectiveHandler.preHandleDirective(directive, directiveHandlerResult)
        nudgeDirectiveHandler.handleDirective("123")

        val payload = MessageFactory.create(nudgePayload, NudgeDirectiveHandler.Payload::class.java)

        verify(nudgeDirectiveObserver).onNudgeAppendDirective("abc", payload!!)
        verify(directiveHandlerResult).setCompleted()
    }

    @Test
    fun test2_handleDirective_nullPayload() {
        val nudgePayload = ""
        val directive = Directive(null, Header("abc", "123", "", "", "", ""), nudgePayload)

        nudgeDirectiveHandler.preHandleDirective(directive, directiveHandlerResult)
        nudgeDirectiveHandler.handleDirective("123")

        //val payload = MessageFactory.create(nudgePayload, NudgeDirectiveHandler.Payload::class.java)
        //println("${name.methodName}  ${payload.toString()}")

        verify(directiveHandlerResult).setFailed("Invalid Payload")
    }

    @Test
    fun test3_handleDirective_nullNudgeInfo() {
        val nudgePayload = "{}"
        val directive = Directive(null, Header("abc", "123", "", "", "", ""), nudgePayload)

        nudgeDirectiveHandler.preHandleDirective(directive, directiveHandlerResult)
        nudgeDirectiveHandler.handleDirective("123")

        //val payload = MessageFactory.create(nudgePayload, NudgeDirectiveHandler.Payload::class.java)
        //println("${name.methodName}  ${payload.toString()}")

        verify(directiveHandlerResult).setFailed("Invalid Payload")
    }

    @Test
    fun test4_handleDirective_emptyNudgeInfo() {
        val nudgePayload = "{\"nudgeInfo\":null}"
        val directive = Directive(null, Header("abc", "123", "", "", "", ""), nudgePayload)

        nudgeDirectiveHandler.preHandleDirective(directive, directiveHandlerResult)
        nudgeDirectiveHandler.handleDirective("123")

        //val payload = MessageFactory.create(nudgePayload, NudgeDirectiveHandler.Payload::class.java)
        //println("${name.methodName}  ${payload.toString()}")

        verify(directiveHandlerResult).setFailed("Invalid Payload")
    }
}