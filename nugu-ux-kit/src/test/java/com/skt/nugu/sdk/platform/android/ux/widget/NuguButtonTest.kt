package com.skt.nugu.sdk.platform.android.ux.widget

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
class NuguButtonTest {

    lateinit var button: NuguButton

    @Before
    fun setup() {
        button = NuguButton(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testDefault() {
        assertTrue(button.isFab())
        assertTrue(button.isClickable)
        assertTrue(button.isFocusable)
        assertTrue(button.isEnabled)
    }

    @Test
    fun testPlayWhenResume() {
        val spy = spy(button)
        spy.pauseAnimation()
        spy.resumeAnimation()

        verify(spy).playAnimation()
    }

    @Test
    fun testEnable() {
        val spy = spy(button)
        spy.isEnabled = true
        spy.isEnabled = false

        verify(spy, times(2)).invalidate()
        verify(spy, never()).stopAnimation()
        verify(spy, never()).playAnimation()
    }

    @Test
    fun testSetButton() {
        val spy = spy(button)
        spy.setButtonType(NuguButton.TYPE_BUTTON)
        spy.setButtonColor(NuguButton.COLOR_WHITE)

        verify(spy, atLeastOnce()).requestLayout()
        verify(spy, atLeastOnce()).invalidate()
    }

    @Test
    fun testDrawableWrong() {
        assertThrows("Illegal color value 100", IllegalArgumentException::class.java) { button.getImageDrawableResId(100) }
    }

    @Test
    fun testDrawableType() {
        assertThrows("Illegal type value 123", IllegalArgumentException::class.java) {
            button.setButtonType(123)
            button.getImageDrawableResId(1)
        }
    }

    @Test
    fun testDrawableWrongColor() {
        assertThrows("Illegal color value 123", IllegalArgumentException::class.java) {
            button.setButtonColor(123)
            button.getImageDrawableResId(1)
        }
    }
}