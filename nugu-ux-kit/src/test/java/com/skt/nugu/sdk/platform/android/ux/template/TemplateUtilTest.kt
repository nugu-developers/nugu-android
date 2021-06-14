package com.skt.nugu.sdk.platform.android.ux.template

import org.junit.Assert
import org.junit.Test

class TemplateUtilTest {

    @Test
    fun test_isSupportVisibleTokenList() {
        val testTrue = "{\"supportVisibleTokenList\":true}"
        val testFalse = "{\"supportVisibleTokenList\":false}"
        val testNull = "{\"supportVisibleTokenList\":null}"
        val emptyString = ""

        Assert.assertFalse(isSupportVisibleTokenList(emptyString))
        Assert.assertFalse(isSupportVisibleTokenList(testNull))
        Assert.assertFalse(isSupportVisibleTokenList(testFalse))
        Assert.assertTrue(isSupportVisibleTokenList(testTrue))
    }
}