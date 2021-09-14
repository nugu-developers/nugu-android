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