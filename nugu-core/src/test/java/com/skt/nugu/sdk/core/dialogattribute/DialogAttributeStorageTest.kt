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

package com.skt.nugu.sdk.core.dialogattribute

import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttribute
import com.skt.nugu.sdk.core.interfaces.dialog.DialogAttributeStorageInterface
import org.junit.Assert
import org.junit.Test

class DialogAttributeStorageTest {
    @Test
    fun testStorage() {
        val storage: DialogAttributeStorageInterface = DialogAttributeStorage()
        val key = "test_key"
        val attribute = DialogAttribute("test_playServiceId", null, null)

        storage.setAttribute(key, attribute)
        Assert.assertEquals(storage.getAttribute(key), attribute)

        storage.removeAttribute(key)
        Assert.assertTrue(storage.getAttribute(key) == null)
    }

    @Test
    fun testGetRecentAttributes() {
        val storage: DialogAttributeStorageInterface = DialogAttributeStorage()
        val key1 = "test_key1"
        val attribute1 = DialogAttribute("test_playServiceId_2", null, null)
        val key2 = "test_key2"
        val attribute2 = DialogAttribute("test_playServiceId_2", null, null)

        storage.setAttribute(key1, attribute1)
        storage.setAttribute(key2, attribute2)
        Assert.assertEquals(storage.getRecentAttribute(), attribute2)
    }
}