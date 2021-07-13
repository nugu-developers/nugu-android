package com.skt.nugu.sdk.platform.android.ux.template

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.view.TemplateNativeView
import com.skt.nugu.sdk.platform.android.ux.template.view.media.DisplayAudioPlayer
import com.skt.nugu.sdk.platform.android.ux.template.webview.TemplateWebView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
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

        TemplateView.templateConstructor[TemplateView.MEDIA_TEMPLATE_TYPES] = { templateType, context ->
            DisplayAudioPlayer(templateType, context).apply { id = R.id.template_view }
        }
    }
}