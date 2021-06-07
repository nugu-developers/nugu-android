package com.skt.nugu.sdk.platform.android.service.webkit

import junit.framework.TestCase
import org.junit.Assert

import org.junit.Assert.*
import org.junit.Test

class JavaScriptHelperTest : TestCase() {
    @Test
    fun testFormatOnRoutineStatusChanged() {
        val expected = "javascript:onRoutineStatusChanged('token', 'status');"
        Assert.assertEquals(expected, JavaScriptHelper.formatOnRoutineStatusChanged("token", "status"))
    }
}