package com.skt.nugu.sdk.platform.android.ux.template.controller

import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.playback.PlaybackButton
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import com.skt.nugu.sdk.platform.android.ux.template.view.media.PlayerCommand
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class DefaultTemplateHandlerTest {
    @Mock
    private lateinit var clientListener: TemplateHandler.ClientListener

    @Mock
    private lateinit var nuguAndroidClient: NuguAndroidClient

    private val nuguAndroidProvider = object : TemplateRenderer.NuguClientProvider {
        override fun getNuguClient(): NuguAndroidClient = nuguAndroidClient
    }

    private val templateInfo = TemplateHandler.TemplateInfo("templateId", "templateType")

    private lateinit var defaultTemplateHandler: DefaultTemplateHandler

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        defaultTemplateHandler = DefaultTemplateHandler(nuguAndroidProvider, templateInfo)
        defaultTemplateHandler.setClientListener(clientListener)
    }

    @Test
    fun testElementSelected() {
        defaultTemplateHandler.onElementSelected("token123")
        verify(nuguAndroidClient).getDisplay()?.setElementSelected(templateInfo.templateId, "token123")
    }

    @Test
    fun testChipSelected() {
        defaultTemplateHandler.onChipSelected("chip string")
        verify(nuguAndroidClient).asrAgent?.stopRecognition()
        verify(nuguAndroidClient).requestTextInput("chip string")
    }

    @Test
    fun testZeroInteractions() {
        defaultTemplateHandler.onCloseClicked()
        verifyZeroInteractions(nuguAndroidClient)

        defaultTemplateHandler.onContextChanged("context")
        verifyZeroInteractions(nuguAndroidClient)

        defaultTemplateHandler.onControlResult("action", "result")
        verifyZeroInteractions(nuguAndroidClient)

        defaultTemplateHandler.showToast("toast")
        verifyZeroInteractions(nuguAndroidClient)


        defaultTemplateHandler.showActivity("className")
        verifyZeroInteractions(nuguAndroidClient)
    }

    @Test
    fun testNuguButtonSelected() {
        defaultTemplateHandler.onNuguButtonSelected()
        verify(nuguAndroidClient).asrAgent?.startRecognition(initiator = ASRAgentInterface.Initiator.TAP)
    }

    @Test
    fun testPlayTTS() {
        defaultTemplateHandler.playTTS("tts")
        verify(nuguAndroidClient).requestTTS("tts")
    }

    @Test
    fun testMediaProgressSending() {
        //periodically sending
        defaultTemplateHandler.startMediaProgressSending()
        verify(clientListener, timeout(2500).times(2)).onMediaProgressChanged(any(), any())

        //after stop. there will be no progress sending
        defaultTemplateHandler.clear()
        verifyNoMoreInteractions(clientListener)
    }

    @Test
    fun testController() {
        defaultTemplateHandler.displayController.controlFocus(Direction.NEXT)
        verify(clientListener).controlFocus(Direction.NEXT)

        defaultTemplateHandler.displayController.controlScroll(Direction.PREVIOUS)
        verify(clientListener).controlScroll(Direction.PREVIOUS)

        defaultTemplateHandler.displayController.getFocusedItemToken()
        verify(clientListener).getFocusedItemToken()

        defaultTemplateHandler.displayController.getVisibleTokenList()
        verify(clientListener).getVisibleTokenList()
    }

    @Test
    fun testPlayerCommandWithEmptyParam(){
        defaultTemplateHandler.onPlayerCommand(PlayerCommand.REPEAT.command, "")
    }

    @Test
    fun testClear(){
        defaultTemplateHandler.clear()
        verify(nuguAndroidClient).audioPlayerAgent?.removeListener(any())
        verify(nuguAndroidClient).audioPlayerAgent?.removeOnDurationListener(any())
    }

}