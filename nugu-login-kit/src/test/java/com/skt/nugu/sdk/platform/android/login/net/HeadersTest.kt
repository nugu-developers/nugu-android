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