/**
 * Copyright (c) 2021 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skt.nugu.sdk.platform.android.ux.widget

import android.os.Build
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.skt.nugu.sdk.agent.chips.Chip
import junit.framework.Assert.assertEquals
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
class ChromeWindowContentLayoutTest {

    private val parent : ViewGroup = FrameLayout(ApplicationProvider.getApplicationContext())
    private val chromeContent = ChromeWindowContentLayout(context = ApplicationProvider.getApplicationContext(), view = parent)


    @Test
    fun testParentLayoutChange(){
        val spy = spy(chromeContent)
        spy.parentLayoutChangeListener.onLayoutChange(parent, 0, 0, 0, 0, 0, 0, 0, 0)

        // STATE NONE
        spy.parentLayoutChangeListener.onLayoutChange(parent, 0, 0, 0, 0, 10, 0, 0, 0)
        assertEquals(parent.height.toFloat(), spy.y)

        chromeContent.y = 20f
        spy.parentLayoutChangeListener.onLayoutChange(parent, 0, 0, 0, 0, 10, 0, 0, 0)
        assertEquals(parent.height.toFloat(), spy.y)
    }

    @Test
    fun testChipsListener(){
        val chromeWindowCallback : ChromeWindowContentLayout.OnChromeWindowContentLayoutCallback = mock()

        // chips click when callback's absence
        val chipsItem = NuguChipsView.Item(text = "request", Chip.Type.GENERAL)
        chromeContent.chipsListener.onClick(chipsItem)

        // set callback
        chromeContent.setOnChromeWindowContentLayoutCallback(chromeWindowCallback)

        // chips click
        chromeContent.chipsListener.onClick(chipsItem)
        verify(chromeWindowCallback).onChipsClicked(chipsItem)

        // chips scrolled
        chromeContent.chipsListener.onScrolled(0, 0)
        chromeContent.hideText()
        chromeContent.chipsListener.onScrolled(10, 0)
        chromeContent.showText()
        chromeContent.chipsListener.onScrolled(10, 0)

        // no meaning.. for line coverage
        chromeContent.hideText()
        chromeContent.setText("Text")
    }
}