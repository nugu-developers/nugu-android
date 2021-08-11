package com.skt.nugu.sdk.platform.android.ux.template.view.media

import android.os.Build
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.client.theme.ThemeManager
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.TemplateView
import com.skt.nugu.sdk.platform.android.ux.template.controller.BasicTemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.model.AudioPlayer
import com.skt.nugu.sdk.platform.android.ux.template.model.AudioPlayerContent
import com.skt.nugu.sdk.platform.android.ux.template.model.AudioPlayerTitle
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.annotation.Config


@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DisplayAudioPlayerTest {

    private lateinit var audioPlayerViewType1: DisplayAudioPlayer
    private lateinit var audioPlayerViewType2: DisplayAudioPlayer

    @Mock
    private lateinit var themeManager: ThemeManager

    @Mock
    private lateinit var nuguAndroidClient: NuguAndroidClient

    @Mock
    private lateinit var nuguClientProvider: TemplateRenderer.NuguClientProvider

    @Mock
    private lateinit var templateInfo: TemplateHandler.TemplateInfo

    @Mock
    private lateinit var fragment: Fragment

    @Mock
    private lateinit var mediaContext: AudioPlayerAgentInterface.Context

    @InjectMocks
    private lateinit var emptyAudioPlayerInfo: AudioPlayer

    @Mock
    private lateinit var emptyAudioPlayerContent: AudioPlayerContent


    private var audioPlayerContent: AudioPlayerContent = AudioPlayerContent(
        "title",
        "subtitle1",
        "subtitle2",
        "imageUrl",
        "durationSec",
        "bgUrl",
        "bgColor",
        "badgeMessage",
        "badgeUrl",
        null,
        null)

    private lateinit var audioPlayerInfo: AudioPlayer

    @Mock
    private lateinit var audioPlayerTitle: AudioPlayerTitle

    @Before
    fun setup() {
        audioPlayerViewType1 = DisplayAudioPlayer(TemplateView.AUDIO_PLAYER_TEMPLATE_1, ApplicationProvider.getApplicationContext())
        audioPlayerViewType2 = DisplayAudioPlayer(TemplateView.AUDIO_PLAYER_TEMPLATE_2, ApplicationProvider.getApplicationContext())
        MockitoAnnotations.openMocks(this)
        whenever(nuguClientProvider.getNuguClient()).thenReturn(nuguAndroidClient)
        whenever(nuguAndroidClient.themeManager).thenReturn(themeManager)

        audioPlayerInfo = AudioPlayer(audioPlayerTitle, audioPlayerContent)
    }

    @Test
    fun test_templateHandler() {
        val playerSpy = spy(audioPlayerViewType1)
        val handlerSpy = spy(BasicTemplateHandler(nuguClientProvider, templateInfo, fragment))
        playerSpy.templateHandler = handlerSpy

        verify(playerSpy).updateThemeIfNeeded()
        verify(handlerSpy).observeMediaState()
        verify(handlerSpy).setClientListener(any())
        verify(handlerSpy, atLeast(2)).getNuguClient()
    }

    @Test
    fun test_mediaListener() {
        val spy = spy(audioPlayerViewType1)
        spy.mediaListener.onMediaStateChanged(AudioPlayerAgentInterface.State.PAUSED, 0L, 0f)
        spy.mediaListener.onMediaStateChanged(AudioPlayerAgentInterface.State.PLAYING, 0L, 0f)

        spy.mediaListener.onMediaDurationRetrieved(1000)
        spy.mediaListener.onMediaProgressChanged(10f, 100)

        spy.load(emptyAudioPlayerInfo, false)
        spy.mediaListener.onMediaDurationRetrieved(1000)
        spy.mediaListener.onMediaProgressChanged(20f, 200)

        spy.load(audioPlayerInfo, false)
        spy.mediaListener.onMediaDurationRetrieved(1000)
        spy.mediaListener.onMediaProgressChanged(30f, 300)

        assertEquals(spy.audioPlayerItem?.content, audioPlayerContent)
    }
}