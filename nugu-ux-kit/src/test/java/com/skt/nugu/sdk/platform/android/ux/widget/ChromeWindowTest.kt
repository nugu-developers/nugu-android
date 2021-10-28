package com.skt.nugu.sdk.platform.android.ux.widget

import android.os.Build
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.chips.Chip
import com.skt.nugu.sdk.agent.chips.RenderDirective
import com.skt.nugu.sdk.agent.dialog.DialogUXStateAggregatorInterface
import com.skt.nugu.sdk.client.theme.ThemeManager
import com.skt.nugu.sdk.client.theme.ThemeManagerInterface
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
class ChromeWindowTest {

    private val themeManager = ThemeManager()

    @Mock
    private lateinit var nuguAndroidClient: NuguAndroidClient

    @Mock
    private lateinit var nuguClientProvider: ChromeWindow.NuguClientProvider

    @Mock
    private lateinit var asrAgent: ASRAgentInterface

    private val customChipsProvider = object : ChromeWindow.CustomChipsProvider {
        override fun onCustomChipsAvailable(isSpeaking: Boolean): Array<Chip>? {
            return if (isSpeaking) arrayOf(Chip(Chip.Type.GENERAL, "chip", "token"))
            else arrayOf(Chip(Chip.Type.ACTION, "chip", "token"))
        }
    }

    private val parent = FrameLayout(ApplicationProvider.getApplicationContext())
    private lateinit var chromeWindow: ChromeWindow

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(nuguClientProvider.getNuguClient()).thenReturn(nuguAndroidClient)
        whenever(nuguAndroidClient.themeManager).thenReturn(themeManager)
        whenever(nuguAndroidClient.asrAgent).thenReturn(asrAgent)

        chromeWindow = ChromeWindow(ApplicationProvider.getApplicationContext(), parent, nuguClientProvider)
    }

    @Test
    fun chromeWindowCallbackTest() {
        val callbackMock: ChromeWindow.OnChromeWindowCallback = mock()

        chromeWindow.setOnChromeWindowCallback(callback = callbackMock)
        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.EXPECTING, false, null, true)

        verify(callbackMock).onExpandStarted()
    }

    @Test
    fun chromeWindowDestroy() {
        chromeWindow.destroy()
        verify(nuguAndroidClient).removeASRResultListener(chromeWindow)
        verify(nuguAndroidClient).removeDialogUXStateListener(chromeWindow)
    }

    @Test
    fun interactionWithContentLayout_LISTENING() {
        val contentMock: ChromeWindowContentLayout = mock()
        chromeWindow.contentLayout = contentMock

        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.LISTENING, false, null, true)
        verify(contentMock).startAnimation(NuguVoiceChromeView.Animation.LISTENING)
        verify(contentMock).hideText()
        verify(contentMock).hideChips()
    }

    @Test
    fun interactionWithContentLayout_THINKING() {
        val contentMock: ChromeWindowContentLayout = mock()
        chromeWindow.contentLayout = contentMock

        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.THINKING, false, null, true)
        verify(contentMock).startAnimation(NuguVoiceChromeView.Animation.THINKING)
    }

    @Test
    fun interactionWithContentLayout_EXPECTING() {
        val contentMock: ChromeWindowContentLayout = mock()
        chromeWindow.contentLayout = contentMock

        // dialogMode == false, chips == null
        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.EXPECTING, false, null, true)
        verify(contentMock).startAnimation(NuguVoiceChromeView.Animation.WAITING)
        verify(contentMock).setHint(R.string.nugu_guide_text)
        verify(contentMock).showText()
        verify(contentMock).updateChips(null)

        // dialogMode == false, chips == null
        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.EXPECTING, true, null, true)
        verify(contentMock).hideText()

        // dialogMode == true, chips == exist
        val payloadDM = RenderDirective.Payload("playServiceId",
            target = RenderDirective.Payload.Target.DM,
            chips = arrayOf(Chip(Chip.Type.GENERAL, "chip", "token")))
        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.EXPECTING, true, payloadDM, true)
        verify(contentMock).updateChips(payloadDM)

        // dialogMode == false, chips == exist
        val payloadLISTEN = RenderDirective.Payload("playServiceId",
            target = RenderDirective.Payload.Target.LISTEN,
            chips = arrayOf(Chip(Chip.Type.GENERAL, "chip", "token")))
        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.EXPECTING, false, payloadLISTEN, true)
        verify(contentMock).updateChips(payloadLISTEN)
    }

    @Test
    fun interactionWithContentLayout_SPEAKING() {
        val contentMock: ChromeWindowContentLayout = mock()
        chromeWindow.contentLayout = contentMock

        val payload = RenderDirective.Payload("playServiceId",
            target = RenderDirective.Payload.Target.SPEAKING,
            chips = arrayOf(Chip(Chip.Type.GENERAL, "chip", "token")))

        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.SPEAKING, false, null, false)
        verify(contentMock).startAnimation(NuguVoiceChromeView.Animation.SPEAKING)
        verify(contentMock).hideText()
        verify(contentMock).hideChips()
        verify(contentMock).dismiss()

        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.SPEAKING, false, payload, false)
        verify(contentMock).updateChips(payload)
    }

    @Test
    fun interactionWithContentLayout_IDLE() {
        val contentMock: ChromeWindowContentLayout = mock()
        chromeWindow.contentLayout = contentMock

        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.IDLE, false, null, false)
        verify(contentMock).dismiss()
    }

    @Test
    fun interactionWithContentLayout() {
        assertEquals(chromeWindow.isShown(), chromeWindow.contentLayout.isExpanded())
        assertEquals(chromeWindow.isChipsEmpty(), chromeWindow.contentLayout.isChipsEmpty())

        val contentMock: ChromeWindowContentLayout = mock()
        chromeWindow.contentLayout = contentMock
        chromeWindow.getGlobalVisibleRect(mock())
        verify(contentMock).getGlobalVisibleRect(any())
    }

    @Test
    fun testASR() {
        val contentMock: ChromeWindowContentLayout = mock()
        chromeWindow.contentLayout = contentMock

        chromeWindow.onError(mock(), mock(), true)
        chromeWindow.onCancel(mock(), mock())
        chromeWindow.onNoneResult(mock())
        verifyZeroInteractions(contentMock)

        chromeWindow.onPartialResult("partial", mock())
        verify(contentMock).setText("partial")

        chromeWindow.onCompleteResult("complete", mock())
        verify(contentMock).setText("complete")
    }

    @Test
    fun testTheme() {
        val contentMock: ChromeWindowContentLayout = mock()
        chromeWindow.contentLayout = contentMock

        themeManager.theme = ThemeManagerInterface.THEME.DARK
        verify(contentMock).setDarkMode(true, ThemeManagerInterface.THEME.DARK)

        themeManager.theme = ThemeManagerInterface.THEME.SYSTEM
        verify(contentMock).setDarkMode(false, ThemeManagerInterface.THEME.SYSTEM)

        themeManager.theme = ThemeManagerInterface.THEME.DARK
        verify(contentMock, atLeastOnce()).setDarkMode(true, ThemeManagerInterface.THEME.DARK)

        themeManager.theme = ThemeManagerInterface.THEME.LIGHT
        verify(contentMock).setDarkMode(false, ThemeManagerInterface.THEME.LIGHT)
    }

    @Test
    fun callbackInvokeTest() {
        val callbackMock: ChromeWindow.OnChromeWindowCallback = mock()
        chromeWindow.setOnChromeWindowCallback(callbackMock)

        // onHidden() test
        chromeWindow.contentLayout.callback!!.onHidden()
        verify(callbackMock).onHiddenFinished()

        // onChipsClicked() test
        val chipsItem = NuguChipsView.Item(text = "request", Chip.Type.GENERAL)
        chromeWindow.contentLayout.callback!!.onChipsClicked(chipsItem)
        verify(nuguAndroidClient).requestTextInput("request")
        verify(asrAgent).stopRecognition()

        // onHidden() and onChipsClick() test when asr null, call back null
        whenever(nuguAndroidClient.asrAgent).thenReturn(null)
        chromeWindow.setOnChromeWindowCallback(null)
        chromeWindow.contentLayout.callback!!.onHidden()
        chromeWindow.contentLayout.callback!!.onChipsClicked(chipsItem)
        verifyNoMoreInteractions(callbackMock)

        // restore asrAgent
        whenever(nuguAndroidClient.asrAgent).thenReturn(asrAgent)

        // test onOutSideTouch() when (isDialogMode == false && isSpeaking() == true)
        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.EXPECTING, false, null, false)
        chromeWindow.contentLayout.callback!!.onOutSideTouch()
        verify(asrAgent, times(2)).stopRecognition()

        // test onOutSideTouch() when (isThinking true)
        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.THINKING, false, null, false)
        chromeWindow.contentLayout.callback!!.onOutSideTouch()
        verifyNoMoreInteractions(asrAgent)

        // test onOutSideTouch() when (isDialogMode == true && isSpeaking() == true)
        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.SPEAKING, true, null, false)
        chromeWindow.contentLayout.callback!!.onOutSideTouch()
        verifyNoMoreInteractions(asrAgent)
    }

    @Test
    fun chromeWindowCustomChipsProvider() {
        val contentMock: ChromeWindowContentLayout = mock()
        chromeWindow.contentLayout = contentMock

        val customChipsProviderSpy = spy(customChipsProvider)
        chromeWindow.setOnCustomChipsProvider(customChipsProviderSpy)

        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.EXPECTING, false, null, true)
        verify(customChipsProviderSpy).onCustomChipsAvailable(false)

        chromeWindow.onDialogUXStateChanged(DialogUXStateAggregatorInterface.DialogUXState.SPEAKING, false, null, true)
        verify(customChipsProviderSpy).onCustomChipsAvailable(true)

        verify(contentMock, times(2)).updateChips(any())
    }

    @Test
    fun setScreenOnWhile(){
        chromeWindow.setScreenOnWhileASR(true)
        chromeWindow.setScreenOnWhileASR(false)
    }
}