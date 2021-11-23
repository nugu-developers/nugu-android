package com.skt.nugu.sdk.platform.android.ux.template.controller

import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.verifyZeroInteractions

class TemplateNuguHandlerTest {
    @Mock
    private lateinit var clientListener: TemplateHandler.ClientListener

    @Mock
    private lateinit var nuguAndroidClient: NuguAndroidClient

    @Mock
    private lateinit var audioPlayerAgent: DefaultAudioPlayerAgent

    private val nuguAndroidProvider = object : TemplateRenderer.NuguClientProvider {
        override fun getNuguClient(): NuguAndroidClient = nuguAndroidClient
    }

    private val templateInfo = TemplateHandler.TemplateInfo("templateId", "templateType")

    private lateinit var nuguTemplateHandler: TemplateNuguHandler

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        nuguTemplateHandler = TemplateNuguHandler(nuguAndroidProvider, templateInfo)
        nuguTemplateHandler.setClientListener(clientListener)
    }

    @Test
    fun testElementSelected() {
        val display: DisplayAggregatorInterface = mock()
        `when`(nuguAndroidClient.getDisplay()).thenReturn(display)

        nuguTemplateHandler.onElementSelected("token123")
        verify(display).setElementSelected(templateInfo.templateId, "token123")

        // check null condition
        `when`(nuguAndroidClient.getDisplay()).thenReturn(null)
        nuguTemplateHandler.onElementSelected("token123")
        verifyNoMoreInteractions(display)
    }

    @Test
    fun testChipSelected() {
        val asrAgent: ASRAgentInterface = mock()
        `when`(nuguAndroidClient.asrAgent).thenReturn(asrAgent)

        nuguTemplateHandler.onChipSelected("chip string")
        verify(asrAgent).stopRecognition()
        verify(nuguAndroidClient).requestTextInput("chip string")

        // check null condition
        `when`(nuguAndroidClient.asrAgent).thenReturn(null)
        nuguTemplateHandler.onChipSelected("chip string")
        verifyNoMoreInteractions(asrAgent)
    }

    @Test
    fun testZeroInteractions() {
        nuguTemplateHandler.onCloseClicked()
        verifyZeroInteractions(nuguAndroidClient)

        nuguTemplateHandler.onContextChanged("context")
        verifyZeroInteractions(nuguAndroidClient)

        nuguTemplateHandler.onControlResult("action", "result")
        verifyZeroInteractions(nuguAndroidClient)

        nuguTemplateHandler.showToast("toast")
        verifyZeroInteractions(nuguAndroidClient)

        nuguTemplateHandler.showActivity("className")
        verifyZeroInteractions(nuguAndroidClient)
    }

    @Test
    fun testNuguButtonSelected() {
        val asrAgent: ASRAgentInterface = mock()
        `when`(nuguAndroidClient.asrAgent).thenReturn(asrAgent)
        nuguTemplateHandler.onNuguButtonSelected()
        verify(asrAgent).startRecognition(initiator = ASRAgentInterface.Initiator.TAP)

        `when`(nuguAndroidClient.asrAgent).thenReturn(null)
        nuguTemplateHandler.onNuguButtonSelected()
        verifyNoMoreInteractions(clientListener)
    }

    @Test
    fun testPlayTTS() {
        nuguTemplateHandler.playTTS("tts")
        verify(nuguAndroidClient).requestTTS("tts")
    }

    @Test
    fun testClear() {
        `when`(nuguAndroidClient.audioPlayerAgent).thenReturn(audioPlayerAgent)

        nuguTemplateHandler.clear()

        // check null condition
        `when`(nuguAndroidClient.audioPlayerAgent).thenReturn(null)
        nuguTemplateHandler.clear()
        verifyNoMoreInteractions(audioPlayerAgent)
    }

    @Test
    fun testMeaninglessForCoverage() {
        nuguTemplateHandler.onCloseAllClicked()
    }

    @Test
    fun testDisplayController(){
        nuguTemplateHandler.displayController.controlFocus(Direction.PREVIOUS)
        verify(clientListener).controlFocus(Direction.PREVIOUS)

        nuguTemplateHandler.displayController.controlScroll(Direction.PREVIOUS)
        verify(clientListener).controlScroll(Direction.PREVIOUS)

        nuguTemplateHandler.displayController.getFocusedItemToken()
        verify(clientListener).getFocusedItemToken()

        nuguTemplateHandler.displayController.getVisibleTokenList()
        verify(clientListener).getVisibleTokenList()
    }

}