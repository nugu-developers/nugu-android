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

import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.Assert
import org.junit.Test


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ChromeWindowContentLayoutTest {
    @Test
    fun setStateTest() {
        val layout = ChromeWindowContentLayoutMock()
        Assert.assertTrue(layout.setState(BottomSheetBehavior.STATE_HIDDEN))
        Assert.assertTrue(layout.behavior.state == BottomSheetBehavior.STATE_SETTLING)
        layout.setState(BottomSheetBehavior.STATE_EXPANDED)
        layout.setState(BottomSheetBehavior.STATE_HIDDEN)
        layout.setState(BottomSheetBehavior.STATE_EXPANDED)
        layout.setState(BottomSheetBehavior.STATE_HIDDEN)
        layout.setState(BottomSheetBehavior.STATE_EXPANDED)
        layout.waitForState(BottomSheetBehavior.STATE_EXPANDED) {
            Assert.assertTrue(layout.behavior.state == it)
        }
    }
}