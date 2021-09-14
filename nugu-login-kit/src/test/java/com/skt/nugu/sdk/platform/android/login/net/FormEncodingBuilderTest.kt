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

class FormEncodingBuilderTest {
    @Test
    fun testSingleAdd() {
        val form = FormEncodingBuilder()
            .add("name", "value")
        Assert.assertEquals(form.toString(), "name=value")
    }
    @Test
    fun testMultiAdd() {
        val form = FormEncodingBuilder()
            .add("name1", "value1")
            .add("name2", "value2")
        Assert.assertEquals(form.toString(), "name2=value2&name1=value1")
    }
}