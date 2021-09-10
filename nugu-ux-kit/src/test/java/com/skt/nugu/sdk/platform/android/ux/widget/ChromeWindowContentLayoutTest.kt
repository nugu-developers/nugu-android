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
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomsheet.BottomSheetBehavior
import junit.framework.Assert.assertEquals
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
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

        // STATE START_SHOWING
        spy.expand()
        spy.parentLayoutChangeListener.onLayoutChange(parent, 0, 0, 0, 0, 10, 0, 0, 0)
        verify(spy).expand()

        // STATE START_HIDING
        spy.dismiss()
        spy.parentLayoutChangeListener.onLayoutChange(parent, 0, 0, 0, 0, 10, 0, 0, 0)
        verify(spy).dismiss()

    }


}