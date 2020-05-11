/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.ext.navigation

import com.google.gson.JsonObject

data class Context(
    val destination: Poi,
    val route: Route,
    val mode: Mode,
    val carrier: String
) {
    data class Route(
        val from: RouteInfo,
        val to: RouteInfo
    ) {
        fun toJsonObject() = JsonObject().apply {
            add("from", from.toJsonObject())
            add("to", to.toJsonObject())
        }
    }

    data class RouteInfo(
        val distanceInMeter: Long,
        val timeInSec: Long,
        val poi: Poi
    ) {
        fun toJsonObject() = JsonObject().apply {
            addProperty("distanceInMeter",distanceInMeter)
            addProperty("timeInSec",timeInSec)
            add("poi",poi.toJsonObject())
        }
    }

    data class Mode(
        val isRouting: Boolean,
        val isSafeDriving: Boolean
    ) {
        fun toJsonObject() = JsonObject().apply {
            addProperty("isRouting", isRouting)
            addProperty("isSafeDriving", isSafeDriving)
        }
    }
}