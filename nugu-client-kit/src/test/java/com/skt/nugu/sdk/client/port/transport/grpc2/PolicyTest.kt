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
package com.skt.nugu.sdk.client.port.transport.grpc2

import com.google.gson.Gson
import org.junit.Assert
import org.junit.Test

class PolicyTest {
    @Test
    fun testPolicy() {
        val gson = Gson()
        val policy = gson.fromJson<Policy>("{\n" +
                "    \"serverPolicies\": [\n" +
                "      {\n" +
                "        \"protocol\": \"H2_GRPC\",\n" +
                "        \"hostname\": \"tes01.sk.com\",\n" +
                "        \"address\": \"111.222.111.111\",\n" +
                "        \"port\": 443,\n" +
                "        \"retryCountLimit\": 2,\n" +
                "        \"connectionTimeout\": 10000,\n" +
                "        \"charge\": \"Normal\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"protocol\": \"H2_GRPC\",\n" +
                "        \"hostname\": \"tes01.sk.com\",\n" +
                "        \"address\": \"111.222.111.222\",\n" +
                "        \"port\": 443,\n" +
                "        \"retryCountLimit\": 2,\n" +
                "        \"connectionTimeout\": 10000,\n" +
                "        \"charge\": \"Normal\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"healthCheckPolicy\": {\n" +
                "      \"ttl\": 120000,\n" +
                "      \"ttlMax\": 300000,\n" +
                "      \"beta\": 40000,\n" +
                "      \"retryCountLimit\": 4,\n" +
                "      \"retryDelay\": 60000,\n" +
                "      \"healthCheckTimeout\": 15000,\n" +
                "      \"accumulationTime\": 3600000\n" +
                "    }\n" +
                "  }", Policy::class.java)

        Assert.assertNotNull(policy)
        Assert.assertNotNull(policy.healthCheckPolicy)
        Assert.assertNotNull(policy.serverPolicy)
        Assert.assertEquals(policy.serverPolicy.size, 2)
    }

    @Test
    fun testHealthCheckPolicy() {
        val gson = Gson()
        val healthCheckPolicy = gson.fromJson<HealthCheckPolicy>("{\n" +
                "      \"ttl\": 120000,\n" +
                "      \"ttlMax\": 300000,\n" +
                "      \"beta\": 40000,\n" +
                "      \"retryCountLimit\": 4,\n" +
                "      \"retryDelay\": 60000,\n" +
                "      \"healthCheckTimeout\": 15000,\n" +
                "      \"accumulationTime\": 3600000\n" +
                "    }" , HealthCheckPolicy::class.java)

        Assert.assertNotNull(healthCheckPolicy)
        Assert.assertEquals(healthCheckPolicy.ttl, 120000)
        Assert.assertEquals(healthCheckPolicy.ttlMax, 300000)
        Assert.assertEquals(healthCheckPolicy.beta, 40000.toFloat())
        Assert.assertEquals(healthCheckPolicy.retryCountLimit, 4)
        Assert.assertEquals(healthCheckPolicy.retryDelay, 60000)
        Assert.assertEquals(healthCheckPolicy.healthCheckTimeout, 15000)
        Assert.assertEquals(healthCheckPolicy.accumulationTime,3600000)
    }
}