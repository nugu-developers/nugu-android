package com.skt.nugu.sdk.platform.android.ux.template.view.media

import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skt.nugu.sdk.platform.android.ux.R
import com.skt.nugu.sdk.platform.android.ux.template.model.LyricsInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LyricsViewTest {
    private lateinit var lyricsView: LyricsView

    @Before
    fun setup() {
        lyricsView = LyricsView(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun test1_initState() {
        assertFalse(lyricsView.isDark)
        assertEquals(lyricsView.viewSize, 1)
    }

    @Test
    fun test2_updateWhenItemSet() {
        val spy = Mockito.spy(lyricsView)
        spy.setItems(listOf(LyricsInfo(0, "0"), LyricsInfo(1, "1")))
        verify(spy).update()
    }

    @Test
    fun test3_updateWhenThemeChanged() {
        val spy = Mockito.spy(lyricsView)
        spy.isDark = true
        verify(spy).update()
    }

    @Test
    fun test4_showEmptyViewWhenEmptyItem() {
        val spy = Mockito.spy(lyricsView)
        spy.setItems(emptyList())
        verify(spy).update()
        assertEquals(spy.findViewById<TextView>(R.id.tv_empty).visibility, View.VISIBLE)
        assertFalse(spy.hasItems())
    }

    @Test
    fun test5_scroll() {
        val spy = Mockito.spy(lyricsView)
        spy.setItems(listOf(LyricsInfo(100, "0"), LyricsInfo(200, "1")))
        spy.setCurrentTimeMs(150)
        verify(spy).scrollToCenter(0)
    }
}