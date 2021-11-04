package com.skt.nugu.sdk.platform.android.ux.template.webview

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.skt.nugu.sdk.agent.DefaultAudioPlayerAgent
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.common.Direction
import com.skt.nugu.sdk.agent.display.DisplayAgentInterface
import com.skt.nugu.sdk.client.theme.ThemeManager
import com.skt.nugu.sdk.client.theme.ThemeManagerInterface
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.TemplateView.Companion.AUDIO_PLAYER_TEMPLATE_1
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateAndroidHandler
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.view.media.PlayerCommand
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
class TemplateWebViewTest{
    private val gson = Gson()

    private lateinit var webView: TemplateWebView

    @Mock
    private lateinit var nuguAndroidClient: NuguAndroidClient

    @Mock
    private lateinit var templateHandler: TemplateAndroidHandler

    @Mock
    private lateinit var displayAgent: DisplayAgentInterface

    @Mock
    private lateinit var themeManager: ThemeManager

    @Mock
    private lateinit var audioPlayerAgent: DefaultAudioPlayerAgent

    private val templateInfo_MEDIA = TemplateHandler.TemplateInfo("templateId", AUDIO_PLAYER_TEMPLATE_1 )
    private val templateInfo_INFO = TemplateHandler.TemplateInfo("templateId", "information" )

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(nuguAndroidClient.themeManager).thenReturn(themeManager)
        whenever(nuguAndroidClient.audioPlayerAgent).thenReturn(audioPlayerAgent)
        whenever(nuguAndroidClient.displayAgent).thenReturn(displayAgent)
        whenever(templateHandler.getNuguClient()).thenReturn(nuguAndroidClient)
        whenever(templateHandler.templateInfo).thenReturn(templateInfo_MEDIA)
        whenever(themeManager.theme).thenReturn(ThemeManagerInterface.THEME.DARK)

        webView = TemplateWebView(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun handlerSetting(){
        val webViewSpy = spy(webView)

        webViewSpy.templateHandler = templateHandler

        verify(webViewSpy).addJavascriptInterface(any(), eq("Android"))
        verify(templateHandler).setClientListener(webViewSpy)
        verify(audioPlayerAgent).setLyricsPresenter(any())
        verify(themeManager).addListener(webViewSpy)
    }

    @Test
    fun lyricPresenter(){
        val webViewSpy = spy(webView)

        assertEquals(webViewSpy.lyricPresenter.getVisibility(), false)
        webViewSpy.lyricPresenter.show()
        webViewSpy.lyricPresenter.hide()
        webViewSpy.lyricPresenter.controlPage(Direction.NEXT)
    }

    @Test
    fun update(){
        val webViewSpy = spy(webView)
        webViewSpy.update("templateContent", "dialogRequestedID")

        webViewSpy.onThemeChange(ThemeManagerInterface.THEME.LIGHT)
        verify(webViewSpy, times(2)).callJSFunction(any())
    }

    @Test
    fun webInterface(){
        val webViewSpy = spy(webView)
        webViewSpy.templateHandler = templateHandler

        webViewSpy.webinterface.close()
        verify(templateHandler).onCloseClicked()

        webViewSpy.webinterface.showToast("toast")
        verify(templateHandler).showToast("toast")

        webViewSpy.webinterface.closeAll()
        verify(templateHandler).onCloseAllClicked()

        webViewSpy.webinterface.playerCommand("next", "param")
        verify(templateHandler).onPlayerCommand(PlayerCommand.NEXT, "param")

        webViewSpy.webinterface.invokeActivity("className")
        verify(templateHandler).showActivity("className")

        webViewSpy.webinterface.requestTTS("tts")
        verify(templateHandler).playTTS("tts")

        webViewSpy.webinterface.onControlResult("action", "result")
        verify(templateHandler).onControlResult("action", "result")

        webViewSpy.webinterface.onContextChanged("context")
        verify(templateHandler).onContextChanged("context")

        webViewSpy.webinterface.onNuguButtonSelected()
        verify(templateHandler).onNuguButtonSelected()

        webViewSpy.webinterface.onChipSelected("chip")
        verify(templateHandler).onChipSelected("chip")
    }

    @Test
    fun mediaStateChange(){
        val webViewSpy = spy(webView)

        webViewSpy.onMediaStateChanged(AudioPlayerAgentInterface.State.IDLE, 0, 0f, true)
        verify(webViewSpy).callJSFunction(JavaScriptHelper.onPlayStopped())

        webViewSpy.onMediaStateChanged(AudioPlayerAgentInterface.State.PLAYING, 0, 0f, true)
        verify(webViewSpy).callJSFunction(JavaScriptHelper.onPlayStarted())

        webViewSpy.onMediaStateChanged(AudioPlayerAgentInterface.State.PAUSED, 0, 0f, true)
        verify(webViewSpy).callJSFunction(JavaScriptHelper.onPlayPaused(true))

        webViewSpy.onMediaStateChanged(AudioPlayerAgentInterface.State.STOPPED, 0, 0f, true)
        verify(webViewSpy, times(2)).callJSFunction(JavaScriptHelper.onPlayStopped())

        webViewSpy.onMediaStateChanged(AudioPlayerAgentInterface.State.FINISHED, 123, 0f, true)
        verify(webViewSpy).callJSFunction(JavaScriptHelper.setProgress(100f))
        verify(webViewSpy).callJSFunction(JavaScriptHelper.onPlayEnd())
    }

    @Test
    fun control(){
        val webViewSpy = spy(webView)

        webViewSpy.controlFocus(Direction.NEXT)
        verify(webViewSpy). callJSFunction(JavaScriptHelper.controlFocus(Direction.NEXT))

        webViewSpy.controlScroll(Direction.PREVIOUS)
        verify(webViewSpy). callJSFunction(JavaScriptHelper.controlScroll(Direction.PREVIOUS))
    }

    @Test
    fun onLoadingComplete(){
        val webViewSpy = spy(webView)
        webViewSpy.templateHandler = templateHandler
        `when`(templateHandler.templateInfo).thenReturn(templateInfo_INFO)

        webViewSpy.onLoadingComplete()
        verify(webViewSpy, never()).callJSFunction(any())
    }

    @Test
    fun onLoadingComplete_media(){
        val webViewSpy = spy(webView)
        webViewSpy.templateHandler = templateHandler

        webViewSpy.onLoadingComplete()
        verify(webViewSpy, atLeastOnce()).callJSFunction(any())
    }

    @Test
    fun userInteraction(){
        val webViewSpy = spy(webView)
        webViewSpy.startNotifyDisplayInteraction()
        verify(displayAgent, never()).notifyUserInteraction(any())
    }
}