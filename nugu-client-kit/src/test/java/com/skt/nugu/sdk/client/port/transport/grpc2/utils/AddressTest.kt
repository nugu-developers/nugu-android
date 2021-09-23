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
package com.skt.nugu.sdk.client.port.transport.grpc2.utils

import junit.framework.TestCase
import org.junit.Assert

import org.junit.Assert.*
import org.junit.Test

class AddressTest : TestCase() {
    @Test
    fun testAddressEquals() {
        Assert.assertTrue(Address("nugu.com", 443) == Address("nugu.com", 443))
        Assert.assertFalse(Address("nugu.com", 443) == Address("nugu.com", 80))
        Assert.assertFalse(Address("nugu1.com", 443) == Address("nugu2.com", 443))
        Assert.assertTrue(Address("nugu2.com", 443).equals(Address("nugu2.com", 443)))
        Assert.assertFalse(Address("", 443) == Address("nugu2.com", 443))
    }

    @Test
    fun testAddressToString() {
        Assert.assertEquals("Address{nugu.com:443}", Address("nugu.com", 443).toString())
    }
}