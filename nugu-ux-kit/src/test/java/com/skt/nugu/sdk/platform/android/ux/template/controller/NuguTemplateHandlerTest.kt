package com.skt.nugu.sdk.platform.android.ux.template.controller

import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import com.skt.nugu.sdk.platform.android.ux.template.view.media.PlayerCommand
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class NuguTemplateHandlerTest {
    @Mock
    private lateinit var clientListener: TemplateHandler.ClientListener

    @Mock
    private lateinit var nuguAndroidClient: NuguAndroidClient

    private val nuguAndroidProvider = object : TemplateRenderer.NuguClientProvider {
        override fun getNuguClient(): NuguAndroidClient = nuguAndroidClient
    }

    private val templateInfo = TemplateHandler.TemplateInfo("templateId", "templateType")

    private lateinit var nuguTemplateHandler: NuguTemplateHandler

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        nuguTemplateHandler = NuguTemplateHandler(nuguAndroidProvider, templateInfo)
        nuguTemplateHandler.setClientListener(clientListener)
    }

    @Test
    fun testElementSelected() {
        nuguTemplateHandler.onElementSelected("token123")
        verify(nuguAndroidClient).getDisplay()?.setElementSelected(templateInfo.templateId, "token123")
    }

    @Test
    fun testChipSelected() {
        nuguTemplateHandler.onChipSelected("chip string")
        verify(nuguAndroidClient).asrAgent?.stopRecognition()
        verify(nuguAndroidClient).requestTextInput("chip string")
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
        nuguTemplateHandler.onNuguButtonSelected()
        verify(nuguAndroidClient).asrAgent?.startRecognition(initiator = ASRAgentInterface.Initiator.TAP)
    }

    @Test
    fun testPlayTTS() {
        nuguTemplateHandler.playTTS("tts")
        verify(nuguAndroidClient).requestTTS("tts")
    }

    @Test
    fun testMediaProgressSending() {
        //periodically sending
        nuguTemplateHandler.startMediaProgressSending()
        verify(clientListener, timeout(2500).times(2)).onMediaProgressChanged(any(), any())

        //after stop. there will be no progress sending
        nuguTemplateHandler.clear()
        verifyNoMoreInteractions(clientListener)
    }

    @Test
    fun testController() {
        nuguTemplateHandler.displayController.controlFocus(Direction.NEXT)
        verify(clientListener).controlFocus(Direction.NEXT)

        nuguTemplateHandler.displayController.controlScroll(Direction.PREVIOUS)
        verify(clientListener).controlScroll(Direction.PREVIOUS)

        nuguTemplateHandler.displayController.getFocusedItemToken()
        verify(clientListener).getFocusedItemToken()

        nuguTemplateHandler.displayController.getVisibleTokenList()
        verify(clientListener).getVisibleTokenList()
    }

    @Test
    fun testPlayerCommandWithEmptyParam(){
        nuguTemplateHandler.onPlayerCommand(PlayerCommand.REPEAT.command, "")
    }

    @Test
    fun testClear(){
        nuguTemplateHandler.clear()
        verify(nuguAndroidClient).audioPlayerAgent?.removeListener(any())
        verify(nuguAndroidClient).audioPlayerAgent?.removeOnDurationListener(any())
    }

}