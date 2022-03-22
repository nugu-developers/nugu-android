package com.skt.nugu.sdk.platform.android.ux.template.controller

import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
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

class TemplateMediaHandlerTest {
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

    private lateinit var mediaTemplateHandler: TemplateMediaHandler

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        mediaTemplateHandler = TemplateMediaHandler(nuguAndroidProvider, templateInfo)
        mediaTemplateHandler.setClientListener(clientListener)
    }

    @Test
    fun testMediaProgressSending() {
        //periodically sending
        mediaTemplateHandler.startMediaProgressSending()
        verify(clientListener, timeout(1500).atLeast(2)).onMediaProgressChanged(any(), any())

        //after stop. there will be no progress sending
        mediaTemplateHandler.clear()
        verifyNoMoreInteractions(clientListener)
    }

    @Test
    fun testMediaRetrieve() {
        mediaTemplateHandler.mediaDurationListener.onRetrieved(1000L, mediaContextSample)
        verify(clientListener).onMediaDurationRetrieved(1000L)

        mediaTemplateHandler.mediaDurationListener.onRetrieved(null, mediaContextSample)
        verify(clientListener).onMediaDurationRetrieved(0L)
    }

    @Test
    fun testMediaStateListener() {
        mediaTemplateHandler.mediaStateListener.onStateChanged(AudioPlayerAgentInterface.State.PLAYING, mediaContextSample)
        verify(clientListener).onMediaStateChanged(eq(AudioPlayerAgentInterface.State.PLAYING), any(), any(), eq(false))

        mediaTemplateHandler.mediaStateListener.onStateChanged(AudioPlayerAgentInterface.State.STOPPED, mediaContextSample)
        verify(clientListener).onMediaStateChanged(eq(AudioPlayerAgentInterface.State.STOPPED), any(), any(), eq(false))

        // check null condition
//        mediaTemplateHandler.setClientListener(null)
//        mediaTemplateHandler.mediaStateListener.onStateChanged(AudioPlayerAgentInterface.State.STOPPED, mediaContextSample)
//        verifyNoMoreInteractions(clientListener)
    }

    @Test
    fun testPlayerCommandWithEmptyParam() {

        whenever(nuguAndroidClient.getPlaybackRouter()).thenReturn(playbackRouter)
        whenever(nuguAndroidClient.audioPlayerAgent).thenReturn(audioPlayerAgent)

        mediaTemplateHandler.onPlayerCommand(PlayerCommand.PLAY, "")
        verify(playbackRouter).buttonPressed(PlaybackButton.PLAY)

        mediaTemplateHandler.onPlayerCommand(PlayerCommand.STOP, "")
        verify(playbackRouter).buttonPressed(PlaybackButton.STOP)

        mediaTemplateHandler.onPlayerCommand(PlayerCommand.PAUSE, "")
        verify(playbackRouter).buttonPressed(PlaybackButton.PAUSE)

        mediaTemplateHandler.onPlayerCommand(PlayerCommand.PREV, "")
        verify(playbackRouter).buttonPressed(PlaybackButton.PREVIOUS)

        mediaTemplateHandler.onPlayerCommand(PlayerCommand.NEXT, "")
        verify(playbackRouter).buttonPressed(PlaybackButton.NEXT)

        mediaTemplateHandler.onPlayerCommand(PlayerCommand.SHUFFLE, "true")
        verify(audioPlayerAgent).requestShuffleCommand(true)

        mediaTemplateHandler.onPlayerCommand(PlayerCommand.SHUFFLE, "")
        verify(audioPlayerAgent).requestShuffleCommand(false)

        AudioPlayerAgentInterface.RepeatMode.values().forEach {
            mediaTemplateHandler.onPlayerCommand(PlayerCommand.REPEAT, it.name)
            verify(audioPlayerAgent).requestRepeatCommand(it)
        }

        mediaTemplateHandler.onPlayerCommand(PlayerCommand.REPEAT, "")

        mediaTemplateHandler.onPlayerCommand(PlayerCommand.FAVORITE, "true")
        verify(audioPlayerAgent).requestFavoriteCommand(true)

        mediaTemplateHandler.onPlayerCommand(PlayerCommand.FAVORITE, "")
        verify(audioPlayerAgent).requestFavoriteCommand(false)

        mediaTemplateHandler.onPlayerCommand(PlayerCommand.UNKNOWN, "")

        `when`(nuguAndroidClient.audioPlayerAgent).thenReturn(null)
        mediaTemplateHandler.onPlayerCommand(PlayerCommand.REPEAT, "ALL")
        mediaTemplateHandler.onPlayerCommand(PlayerCommand.SHUFFLE, "")
        mediaTemplateHandler.onPlayerCommand(PlayerCommand.FAVORITE, "true")

        verifyNoMoreInteractions(audioPlayerAgent)
        verifyNoMoreInteractions(playbackRouter)
    }

    @Test
    fun testPauseDirective() {
        val directive: Directive = mock()
        `when`(directive.getNamespace()).thenReturn(DefaultAudioPlayerAgent.NAMESPACE)
        `when`(directive.getName()).thenReturn(DefaultAudioPlayerAgent.NAME_PAUSE)
        mediaTemplateHandler.directiveHandlingListener.onCompleted(directive)
        verify(clientListener).onMediaStateChanged(eq(AudioPlayerAgentInterface.State.PAUSED), any(), any(), eq(true))

        // check not PAUSE directive
        `when`(directive.getNamespace()).thenReturn("I'm not")
        `when`(directive.getName()).thenReturn("PAUSE")
        mediaTemplateHandler.directiveHandlingListener.onCompleted(directive)
        verifyNoMoreInteractions(clientListener)

        // check null condition
        mediaTemplateHandler.setClientListener(null)
        `when`(directive.getNamespace()).thenReturn(DefaultAudioPlayerAgent.NAMESPACE)
        `when`(directive.getName()).thenReturn(DefaultAudioPlayerAgent.NAME_PAUSE)
        mediaTemplateHandler.directiveHandlingListener.onCompleted(directive)
        verifyNoMoreInteractions(clientListener)

        // meaningless
        mediaTemplateHandler.directiveHandlingListener.onRequested(directive)
        mediaTemplateHandler.directiveHandlingListener.onCanceled(directive)
        mediaTemplateHandler.directiveHandlingListener.onFailed(directive, "")
    }

    @Test
    fun observeMediaState() {
//        `when`(nuguAndroidClient.audioPlayerAgent).thenReturn(audioPlayerAgent)
//
//        mediaTemplateHandler.setClientListener(clientListener)
//
//        verify(audioPlayerAgent).removeListener(mediaTemplateHandler.mediaStateListener)
//        verify(audioPlayerAgent).addListener(mediaTemplateHandler.mediaStateListener)
//        verify(audioPlayerAgent).removeOnDurationListener(mediaTemplateHandler.mediaDurationListener)
//        verify(audioPlayerAgent).addOnDurationListener(mediaTemplateHandler.mediaDurationListener)
//        verify(nuguAndroidClient, atLeastOnce()).addOnDirectiveHandlingListener(mediaTemplateHandler.directiveHandlingListener)
//
//
//        // check null condition
//        `when`(nuguAndroidClient.audioPlayerAgent).thenReturn(null)
//        verifyNoMoreInteractions(audioPlayerAgent)
    }

    @Test
    fun testClear() {
        `when`(nuguAndroidClient.audioPlayerAgent).thenReturn(audioPlayerAgent)

        mediaTemplateHandler.clear()

        verify(audioPlayerAgent).removeListener(mediaTemplateHandler.mediaStateListener)
        verify(audioPlayerAgent).removeOnDurationListener(mediaTemplateHandler.mediaDurationListener)

        // check null condition
        `when`(nuguAndroidClient.audioPlayerAgent).thenReturn(null)
        mediaTemplateHandler.clear()
        verifyNoMoreInteractions(audioPlayerAgent)
        verify(nuguAndroidClient, times(2)).removeOnDirectiveHandlingListener(mediaTemplateHandler.directiveHandlingListener)
    }
}