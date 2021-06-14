package com.skt.nugu.sdk.agent.nudge

import com.skt.nugu.sdk.agent.DefaultASRAgent
import com.skt.nugu.sdk.agent.DefaultTTSAgent
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextSetterInterface
import com.skt.nugu.sdk.core.interfaces.context.ContextType
import com.skt.nugu.sdk.core.interfaces.context.StateRefreshPolicy
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class NudgeAgentTest {
    @get:Rule
    val testName = TestName()

    private lateinit var nudgeAgent: NudgeAgent
    private lateinit var setter: ContextSetterInterface

    @Before
    fun setup() {
        nudgeAgent = NudgeAgent(mock(), mock())
        setter = Mockito.mock(ContextSetterInterface::class.java)

        val payload = "payload"

        val nudgeDirective = Directive(null, Header("abc", "123", NudgeDirectiveHandler.NAME_DIRECTIVE, NudgeAgent.NAMESPACE, "", ""), payload)
        val expectSpeechDirective =
            Directive(null, Header("abc", "123", DefaultASRAgent.NAME_EXPECT_SPEECH, DefaultASRAgent.NAMESPACE, "", ""), payload)
        val speakTTSDirective = Directive(null, Header("abc", "123", DefaultTTSAgent.SPEAK.name, DefaultTTSAgent.SPEAK.namespace, "", ""), payload)

        nudgeAgent.onPreProcessed(listOf(nudgeDirective, expectSpeechDirective, speakTTSDirective))

        //val nudgeDirective = listOf(directive).find { NudgeDirectiveHandler.isAppendDirective(it.header.namespace, it.header.name) }
        //println("${testName.methodName}, $nudgeDirective")
    }

    @Test
    fun test_preProcess_nudgeDirective() {
        Thread.sleep(500)
        assertEquals(nudgeAgent.nudgeData!!.dialogRequestId, "abc")
        assertTrue(nudgeAgent.nudgeData!!.expectSpeechExist)
        assertTrue(nudgeAgent.nudgeData!!.speakTTSExist)
        assertFalse(nudgeAgent.nudgeData!!.displayTemplateExist)
    }

    @Test
    fun test_onNudgeAppendDirective() {
        val nudgePayload = "{\"nudgeInfo\":{\"key\":\"value\"}}"
        val payload = MessageFactory.create(nudgePayload, NudgeDirectiveHandler.Payload::class.java)

        nudgeAgent.onNudgeAppendDirective("abc", payload!!)

        Thread.sleep(500)
        assertEquals(nudgeAgent.nudgeData!!.nudgeInfo, payload.nudgeInfo)
    }

    @Test
    fun test_clearNudgeInfo() {
        nudgeAgent.clearNudgeData()

        Thread.sleep(500)
        assertNull(nudgeAgent.nudgeData)
    }

    @Test
    fun test_provideState() {
        // add nudgeInfo
        val nudgePayload = "{\"nudgeInfo\":{\"key\":\"value\"}}"
        val payload = MessageFactory.create(nudgePayload, NudgeDirectiveHandler.Payload::class.java)
        nudgeAgent.onNudgeAppendDirective("abc", payload!!)

        // full context
        nudgeAgent.provideState(setter, NamespaceAndName(NudgeAgent.NAMESPACE, NudgeDirectiveHandler.NAME_DIRECTIVE), ContextType.FULL, 1)
        Thread.sleep(500)
        verify(setter).setState(namespaceAndName = NamespaceAndName(NudgeAgent.NAMESPACE, NudgeDirectiveHandler.NAME_DIRECTIVE),
            state = NudgeAgent.StateContext(nudgeAgent.nudgeData!!.nudgeInfo!!),
            refreshPolicy = StateRefreshPolicy.ALWAYS,
            type = ContextType.FULL,
            stateRequestToken = 1)

        // and then compact context
        nudgeAgent.provideState(setter, NamespaceAndName(NudgeAgent.NAMESPACE, NudgeDirectiveHandler.NAME_DIRECTIVE), ContextType.COMPACT, 1)
        Thread.sleep(500)
        verify(setter).setState(namespaceAndName = NamespaceAndName(NudgeAgent.NAMESPACE, NudgeDirectiveHandler.NAME_DIRECTIVE),
            state = NudgeAgent.StateContext.CompactContextState,
            refreshPolicy = StateRefreshPolicy.NEVER,
            type = ContextType.COMPACT,
            stateRequestToken = 1)

        nudgeAgent.clearNudgeData()
        Thread.sleep(500)

        //and then full context with no nudgeData
        nudgeAgent.provideState(setter, NamespaceAndName(NudgeAgent.NAMESPACE, NudgeDirectiveHandler.NAME_DIRECTIVE), ContextType.FULL, 1)
        Thread.sleep(500)
        verify(setter).setState(namespaceAndName = NamespaceAndName(NudgeAgent.NAMESPACE, NudgeDirectiveHandler.NAME_DIRECTIVE),
            state = NudgeAgent.StateContext.CompactContextState,
            refreshPolicy = StateRefreshPolicy.ALWAYS,
            type = ContextType.FULL,
            stateRequestToken = 1)
    }

    @Test
    fun test_provideState_emptyNudgeInfo() {
        nudgeAgent.provideState(setter, NamespaceAndName(NudgeAgent.NAMESPACE, NudgeDirectiveHandler.NAME_DIRECTIVE), ContextType.FULL, 1)

        Thread.sleep(500)
        verify(setter).setState(namespaceAndName = NamespaceAndName(NudgeAgent.NAMESPACE, NudgeDirectiveHandler.NAME_DIRECTIVE),
            state = NudgeAgent.StateContext.CompactContextState,
            refreshPolicy = StateRefreshPolicy.ALWAYS,
            type = ContextType.FULL,
            stateRequestToken = 1)
    }
}