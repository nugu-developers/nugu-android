package com.skt.nugu.sdk.core.network

import org.junit.Test

import org.junit.Assert.*

class HealthCheckTest {
    /*
           * next_health_check_time = min(now + ttl_max, now + ttl - beta * log(rnd(0,2)))
            * data received from gRPC(Registry Service)
            beta_ = 0.2
            healthCheckTimeout_ = 2000
            retryCountLimit_ = 5
            retryDelay_ = 0
            ttlMax_ = 0
            ttl_ = 300000
        */
    fun next_health_check_time_v1_isCorrect() {
        val ttl_max : Long = 0
        val ttl : Long = 300000
        val beta = 0.2
        val now = System.currentTimeMillis()
        val next_health_check_time = Math.min(now + ttl_max, now + ttl  - (beta * Math.log(1.0)).toLong())
        assertEquals(true, now > next_health_check_time  )
    }

    //next_health_check_time = max( now + ttl_max + beta * log_e(0 < rand() <= 1) - ttl_max * (누적 헬스 체크 실패 카운트 / 누적 헬스체크 시도 카운트), now + retry_delay)
    @Test
    fun next_health_check_time_v2_isCorrect() {
        val ttl_max : Long =  30000
        val ttl : Long = 30000
        val beta = 2.0
        val now = System.currentTimeMillis()
        val next_health_check_time = Math.max(now + ttl_max + (beta * Math.log(0.1)).toLong() - ttl_max , now + ttl)
        println(ttl_max + (beta * Math.log(0.1)).toLong() - ttl_max * 30/4000)
        println(next_health_check_time)
        assertEquals(true, now < next_health_check_time  )
    }
}