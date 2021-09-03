package com.skt.nugu.sdk.platform.android.ux.template.controller

import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.agent.playback.PlaybackButton
import com.skt.nugu.sdk.agent.playback.PlaybackRouter
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import com.skt.nugu.sdk.platform.android.ux.template.view.media.PlayerCommand
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class NuguTemplateHandlerTest {
    @Mock
    private lateinit var clientListener: TemplateHandler.ClientListener

    @Mock
    private lateinit var nuguAndroidClient: NuguAndroidClient

    @Mock
    private lateinit var audioPlayerAgent: DefaultAudioPlayerAgent

    @Mock
    private lateinit var playbackRouter: PlaybackRouter

    private val nuguAndroidProvider = object : TemplateRenderer.NuguClientProvider {
        override fun getNuguClient(): NuguAndroidClient = nuguAndroidClient
    }

    private var mediaContextSample = AudioPlayerAgentInterface.Context("", "templateId", "", 10L, "")

    private var mediaContextDiffSample = AudioPlayerAgentInterface.Context("", "templateIdDiff", "", 10L, "")

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
    fun testMediaProgressSending() {
        //periodically sending
        nuguTemplateHandler.startMediaProgressSending()
        verify(clientListener, timeout(2500).times(2)).onMediaProgressChanged(any(), any())

        //after stop. there will be no progress sending
        nuguTemplateHandler.clear()
        verifyNoMoreInteractions(clientListener)
    }

    @Test
    fun testMediaRetrieve() {
        nuguTemplateHandler.mediaDurationListener.onRetrieved(1000L, mediaContextSample)
        verify(clientListener).onMediaDurationRetrieved(1000L)

        nuguTemplateHandler.mediaDurationListener.onRetrieved(null, mediaContextSample)
        verify(clientListener).onMediaDurationRetrieved(0L)

        nuguTemplateHandler.mediaDurationListener.onRetrieved(null, mediaContextDiffSample)
        verifyNoMoreInteractions(clientListener)

        // check null condition
        nuguTemplateHandler.setClientListener(null)
        nuguTemplateHandler.mediaDurationListener.onRetrieved(1000L, mediaContextSample)
        verifyNoMoreInteractions(clientListener)
    }

    @Test
    fun testMediaStateListener() {
        nuguTemplateHandler.mediaStateListener.onStateChanged(AudioPlayerAgentInterface.State.PLAYING, mediaContextSample)
        verify(clientListener).onMediaStateChanged(eq(AudioPlayerAgentInterface.State.PLAYING), any(), any(), eq(false))

        nuguTemplateHandler.mediaStateListener.onStateChanged(AudioPlayerAgentInterface.State.STOPPED, mediaContextSample)
        verify(clientListener).onMediaStateChanged(eq(AudioPlayerAgentInterface.State.STOPPED), any(), any(), eq(false))

        // check null condition
        nuguTemplateHandler.setClientListener(null)
        nuguTemplateHandler.mediaStateListener.onStateChanged(AudioPlayerAgentInterface.State.STOPPED, mediaContextSample)
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

        // check null condition
        nuguTemplateHandler.setClientListener(null)

        nuguTemplateHandler.displayController.controlFocus(Direction.NEXT)
        nuguTemplateHandler.displayController.controlScroll(Direction.PREVIOUS)
        nuguTemplateHandler.displayController.getFocusedItemToken()
        nuguTemplateHandler.displayController.getVisibleTokenList()
        verifyNoMoreInteractions(clientListener)
    }

    @Test
    fun testPlayerCommandWithEmptyParam() {

        whenever(nuguAndroidClient.getPlaybackRouter()).thenReturn(playbackRouter)
        whenever(nuguAndroidClient.audioPlayerAgent).thenReturn(audioPlayerAgent)

        nuguTemplateHandler.onPlayerCommand(PlayerCommand.PLAY.command, "")
        verify(playbackRouter).buttonPressed(PlaybackButton.PLAY)

        nuguTemplateHandler.onPlayerCommand(PlayerCommand.STOP.command, "")
        verify(playbackRouter).buttonPressed(PlaybackButton.STOP)

        nuguTemplateHandler.onPlayerCommand(PlayerCommand.PAUSE.command, "")
        verify(playbackRouter).buttonPressed(PlaybackButton.PAUSE)

        nuguTemplateHandler.onPlayerCommand(PlayerCommand.PREV.command, "")
        verify(playbackRouter).buttonPressed(PlaybackButton.PREVIOUS)

        nuguTemplateHandler.onPlayerCommand(PlayerCommand.NEXT.command, "")
        verify(playbackRouter).buttonPressed(PlaybackButton.NEXT)

        nuguTemplateHandler.onPlayerCommand(PlayerCommand.SHUFFLE.command, "true")
        verify(audioPlayerAgent).requestShuffleCommand(true)

        nuguTemplateHandler.onPlayerCommand(PlayerCommand.SHUFFLE.command, "")
        verify(audioPlayerAgent).requestShuffleCommand(false)

        AudioPlayerAgentInterface.RepeatMode.values().forEach {
            nuguTemplateHandler.onPlayerCommand(PlayerCommand.REPEAT.command, it.name)
            verify(audioPlayerAgent).requestRepeatCommand(it)
        }

        nuguTemplateHandler.onPlayerCommand(PlayerCommand.REPEAT.command, "")

        nuguTemplateHandler.onPlayerCommand(PlayerCommand.FAVORITE.command, "true")
        verify(audioPlayerAgent).requestFavoriteCommand(true)

        nuguTemplateHandler.onPlayerCommand(PlayerCommand.FAVORITE.command, "")
        verify(audioPlayerAgent).requestFavoriteCommand(false)

        nuguTemplateHandler.onPlayerCommand("??", "")

        `when`(nuguAndroidClient.audioPlayerAgent).thenReturn(null)
        nuguTemplateHandler.onPlayerCommand(PlayerCommand.REPEAT.command, "ALL")
        nuguTemplateHandler.onPlayerCommand(PlayerCommand.SHUFFLE.command, "")
        nuguTemplateHandler.onPlayerCommand(PlayerCommand.FAVORITE.command, "true")

        verifyNoMoreInteractions(audioPlayerAgent)
        verifyNoMoreInteractions(playbackRouter)
    }

    @Test
    fun testPauseDirective() {
        val directive: Directive = mock()
        `when`(directive.getNamespaceAndName()).thenReturn(NamespaceAndName(DefaultAudioPlayerAgent.NAMESPACE, DefaultAudioPlayerAgent.NAME_PAUSE))
        nuguTemplateHandler.directiveHandlingListener.onCompleted(directive)
        verify(clientListener).onMediaStateChanged(eq(AudioPlayerAgentInterface.State.PAUSED), any(), any(), eq(true))

        // check not PAUSE directive
        `when`(directive.getNamespaceAndName()).thenReturn(NamespaceAndName("I'm not", "PAUSE"))
        nuguTemplateHandler.directiveHandlingListener.onCompleted(directive)
        verifyNoMoreInteractions(clientListener)

        // check null condition
        nuguTemplateHandler.setClientListener(null)
        `when`(directive.getNamespaceAndName()).thenReturn(NamespaceAndName(DefaultAudioPlayerAgent.NAMESPACE, DefaultAudioPlayerAgent.NAME_PAUSE))
        nuguTemplateHandler.directiveHandlingListener.onCompleted(directive)
        verifyNoMoreInteractions(clientListener)

        // meaningless
        nuguTemplateHandler.directiveHandlingListener.onRequested(directive)
        nuguTemplateHandler.directiveHandlingListener.onCanceled(directive)
        nuguTemplateHandler.directiveHandlingListener.onFailed(directive, "")
    }

    @Test
    fun observeMediaState() {
        `when`(nuguAndroidClient.audioPlayerAgent).thenReturn(audioPlayerAgent)

        nuguTemplateHandler.observeMediaState()
        verify(audioPlayerAgent).removeListener(nuguTemplateHandler.mediaStateListener)
        verify(audioPlayerAgent).addListener(nuguTemplateHandler.mediaStateListener)
        verify(audioPlayerAgent).removeOnDurationListener(nuguTemplateHandler.mediaDurationListener)
        verify(audioPlayerAgent).addOnDurationListener(nuguTemplateHandler.mediaDurationListener)
        verify(nuguAndroidClient).addOnDirectiveHandlingListener(nuguTemplateHandler.directiveHandlingListener)


        // check null condition
        `when`(nuguAndroidClient.audioPlayerAgent).thenReturn(null)
        nuguTemplateHandler.observeMediaState()
        verifyNoMoreInteractions(audioPlayerAgent)
    }

    @Test
    fun testClear() {
        `when`(nuguAndroidClient.audioPlayerAgent).thenReturn(audioPlayerAgent)

        nuguTemplateHandler.clear()

        verify(audioPlayerAgent).removeListener(nuguTemplateHandler.mediaStateListener)
        verify(audioPlayerAgent).removeOnDurationListener(nuguTemplateHandler.mediaDurationListener)

        // check null condition
        `when`(nuguAndroidClient.audioPlayerAgent).thenReturn(null)
        nuguTemplateHandler.clear()
        verifyNoMoreInteractions(audioPlayerAgent)
        verify(nuguAndroidClient, times(2)).removeOnDirectiveHandlingListener(nuguTemplateHandler.directiveHandlingListener)
    }

    @Test
    fun testMeaninglessForCoverage() {
        nuguTemplateHandler.onCloseAllClicked()
    }

}