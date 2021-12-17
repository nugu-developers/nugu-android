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

package com.skt.nugu.sdk.core.dialogattribute

import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttribute
import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttributeStorageInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DialogAttributeStorage : DialogAttributeStorageInterface {
    companion object {
        private const val TAG = "DialogAttributeStorage"
    }

    private val lock = ReentrantLock()
    private val attrs: LinkedHashMap<String, DialogAttribute> =
        LinkedHashMap()

    override fun setAttribute(key: String, attr: DialogAttribute) {
        lock.withLock {
            Logger.d(TAG, "[setAttribute] key: $key, attr: $attr")
            attrs[key] = attr
        }
    }

    override fun getAttribute(key: String): DialogAttribute? =
        lock.withLock {
            Logger.d(TAG, "[getAttribute] key: $key")
            attrs[key]
        }

    override fun getRecentAttribute(): DialogAttribute? = lock.withLock {
        attrs.values.lastOrNull().also {
            Logger.d(TAG, "[getRecentAttribute] attr: $it")
        }
    }

    override fun removeAttribute(key: String) {
        lock.withLock {
            attrs.remove(key).let {
                Logger.d(TAG, "[removeAttributes] key: $key, attr: $it")
            }
        }
    }
}