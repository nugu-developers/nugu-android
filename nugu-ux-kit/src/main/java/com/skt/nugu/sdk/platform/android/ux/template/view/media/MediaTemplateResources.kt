/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
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
package com.skt.nugu.sdk.platform.android.ux.template.view.media

import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import com.skt.nugu.sdk.platform.android.ux.R

open class MediaTemplateResources {
    @LayoutRes
    open val layoutResIdPort = R.layout.view_display_audioplayer_port

    @LayoutRes
    open val layoutResIdLand = R.layout.view_display_audioplayer_land

    @DrawableRes
    open val nuguLogoPlaceHolder: Int = R.drawable.nugu_logo_placeholder_60

    @DrawableRes
    open val nuguLogoDefault: Int = R.drawable.nugu_logo_60_line

    @DrawableRes
    open val repeatAllResId: Int = R.drawable.nugu_btn_repeat

    @DrawableRes
    open val repeatOneResId: Int = R.drawable.nugu_btn_repeat_1

    @DrawableRes
    open val repeatNoneResId: Int = R.drawable.nugu_btn_repeat_inactive

    open val mainImageRoundingRadiusDp: Float = 10.7f
    open val badgeImageRoundingRadiusDp: Float = 2f

    open val barPlayerTransitionDurationMs = 400L

    open val albumCoverElevation: Float = 11f
}