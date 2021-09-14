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
package com.skt.nugu.sdk.platform.android.login.net

import org.junit.Assert
import org.junit.Test

class HeadersTest {
    @Test
    fun testHeaders() {
        val header = Headers()
            .add("name1", "value1")
            .add("name2", "value2")
        Assert.assertEquals(header.size(), 2)
        Assert.assertEquals(header.names().size, 2)
        Assert.assertEquals(header.value(0), "value1")
        Assert.assertEquals(header.value(1), "value2")
        Assert.assertEquals(header.name(0), "name1")
        Assert.assertEquals(header.name(1), "name2")
        Assert.assertEquals(header.values("name2").size, 1)
        header.add("name3", "value3")
        Assert.assertEquals(header.size(), 3)
    }
}