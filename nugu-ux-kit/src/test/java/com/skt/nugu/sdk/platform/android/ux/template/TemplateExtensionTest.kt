package com.skt.nugu.sdk.platform.android.ux.template

import android.os.Build
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.widget.NuguButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
class TemplateExtensionTest {

    @Test
    fun updateTextTest() {
        val textView = TextView(ApplicationProvider.getApplicationContext())

        textView.updateText("abc")
        assertEquals(textView.text.toString(), "abc")

        // text test
        textView.updateText("abcd", isMerge = false, maintainLayout = false)
        assertEquals(textView.text.toString(), "abcd", )

        textView.updateText("", isMerge = true, maintainLayout = false)
        assertEquals(textView.text.toString(), "abcd")

        textView.updateText("", isMerge = false, maintainLayout = false)
        assertEquals(textView.text.toString(), "")

        textView.updateText("abcd", isMerge = true, maintainLayout = false)
        assertEquals(textView.text.toString(), "abcd")

        textView.updateText(null, isMerge = true, maintainLayout = false)
        verifyNoMoreInteractions(spy(textView))

        // keep layout test
        textView.visibility = View.VISIBLE
        textView.updateText(null, isMerge = false, maintainLayout = false)
        assertEquals(textView.visibility, View.GONE)

        textView.updateText(null, isMerge = false, maintainLayout = true)
        assertEquals(textView.visibility, View.INVISIBLE)
    }

    @Test
    fun updateImageTest() {
        val imageView = ImageView(ApplicationProvider.getApplicationContext())

        imageView.visibility = View.VISIBLE
        imageView.updateImage(null, null, isMerge = true)
        verifyNoMoreInteractions(spy(imageView))
        assertEquals(imageView.visibility, View.VISIBLE)

        imageView.updateImage("", null, isMerge = true)
        verifyNoMoreInteractions(spy(imageView))

        imageView.updateImage("temp url", null)
        assertEquals(imageView.visibility, View.VISIBLE)

        imageView.updateImage(null, null, isMerge = false)
        assertEquals(imageView.visibility, View.GONE)

        imageView.updateImage("temp url", null, isMerge = false)
        assertEquals(imageView.visibility, View.VISIBLE)

        imageView.updateImage("temp url", transformation = RoundedCorners(NuguButton.dpToPx(2f, ApplicationProvider.getApplicationContext())),
            isMerge = true,
            placeHolder = R.drawable.nugu_btn_random_inactive,
            loadingFailImage = R.drawable.nugu_btn_random_inactive)
        assertEquals(imageView.visibility, View.VISIBLE)
    }

    @Test
    fun marqueeTest() {
        val textView = TextView(ApplicationProvider.getApplicationContext())
        textView.enableMarquee()
        assertEquals(textView.maxLines, 1)
        assertTrue(textView.isSelected)
        assertEquals(textView.ellipsize, TextUtils.TruncateAt.MARQUEE)

    }

}