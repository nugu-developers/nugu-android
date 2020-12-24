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
package com.skt.nugu.sdk.client.configuration

import android.content.Context
import android.content.res.Resources
import java.io.IOException

/**
 * Configure from asset
 */
@Throws(IOException::class)
fun ConfigurationStore.configure(context: Context, filename: String = "nugu-config.json") {
    val assetManager = context.resources.assets
    val inputStream = try {
        assetManager.open(filename)
    } catch (e: IOException) {
        throw Throwable("Asset file($filename) is missing.\nThe NUGU SDK cannot function without it.\n$e")
    }
    configure(inputStream)
}

/**
 * Configure from resources
 */
@Throws(Resources.NotFoundException::class)
fun ConfigurationStore.configure(context: Context, resourceId: Int) {
    val resource = context.resources
    val inputStream = try {
        resource.openRawResource(resourceId)
    } catch (e: Resources.NotFoundException) {
        throw Throwable("Resource $resourceId is missing.\nThe NUGU SDK cannot function without it.\n$e")
    }
    configure(inputStream)
}

