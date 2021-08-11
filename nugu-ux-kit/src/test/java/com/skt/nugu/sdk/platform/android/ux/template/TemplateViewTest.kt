package com.skt.nugu.sdk.platform.android.ux.template

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.view.TemplateNativeView
import com.skt.nugu.sdk.platform.android.ux.template.view.media.DisplayAudioPlayer
import com.skt.nugu.sdk.platform.android.ux.template.webview.TemplateWebView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
class TemplateViewTest {

    @Test
    fun test_createTemplateView() {
        val templateViewWeb = TemplateView.createView("templateType", ApplicationProvider.getApplicationContext())
        assertTrue(templateViewWeb is TemplateWebView)

        val templateViewNative = TemplateView.createView(TemplateView.AUDIO_PLAYER_TEMPLATE_1, ApplicationProvider.getApplicationContext())
        assertTrue(templateViewNative is TemplateNativeView)
        assertTrue(templateViewNative is DisplayAudioPlayer)

        val templateViewNative2 = TemplateView.createView(TemplateView.AUDIO_PLAYER_TEMPLATE_2, ApplicationProvider.getApplicationContext())
        assertTrue(templateViewNative2 is TemplateNativeView)
        assertTrue(templateViewNative2 is DisplayAudioPlayer)

        TemplateView.templateConstructor.clear()
        val templateViewWeb2 = TemplateView.createView(TemplateView.AUDIO_PLAYER_TEMPLATE_2, ApplicationProvider.getApplicationContext())
        assertTrue(templateViewWeb2 is TemplateWebView)

        // return to original settings
        TemplateView.templateConstructor[TemplateView.MEDIA_TEMPLATE_TYPES] = { templateType, context ->
            DisplayAudioPlayer(templateType, context).apply { id = R.id.template_view }
        }

        val templateViewForceToWeb =
            TemplateView.createView(TemplateView.AUDIO_PLAYER_TEMPLATE_2, ApplicationProvider.getApplicationContext(), forceToWebView = true)
        assertTrue(templateViewForceToWeb is TemplateWebView)
    }

    @Test
    fun test_nuguButtonColor() {
        TemplateView.nuguButtonColor = TemplateView.Companion.NuguButtonColor.BLUE
        TemplateView.nuguButtonColor = TemplateView.Companion.NuguButtonColor.WHITE
        GlobalScope.launch {
            delay(500)
//            verify(spy(TemplateView.nuguButtonColorFlow)).emit(TemplateView.nuguButtonColor)
        }

        TemplateView.nuguButtonColor = TemplateView.Companion.NuguButtonColor.WHITE
        GlobalScope.launch {
            delay(500)
            verifyNoMoreInteractions(spy(TemplateView.nuguButtonColorFlow))
        }
    }

    @Test
    fun test_url(){
        val templateViewWeb = TemplateView.createView("templateType", ApplicationProvider.getApplicationContext())
        assertTrue(templateViewWeb is TemplateWebView)
        templateViewWeb.setServerUrl("urlurl")
    }
}