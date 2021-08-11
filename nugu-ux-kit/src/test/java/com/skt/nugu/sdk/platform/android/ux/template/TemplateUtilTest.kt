package com.skt.nugu.sdk.platform.android.ux.template

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skt.nugu.sdk.platform.android.ux.R
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
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

    @Test
    fun test_isSupportFocusedItemToken() {
        val testTrue = "{\"supportFocusedItemToken\":true}"
        val testFalse = "{\"supportFocusedItemToken\":false}"
        val testNull = "{\"supportFocusedItemToken\":null}"
        val emptyString = ""

        Assert.assertFalse(isSupportFocusedItemToken(emptyString))
        Assert.assertFalse(isSupportFocusedItemToken(testNull))
        Assert.assertFalse(isSupportFocusedItemToken(testFalse))
        Assert.assertTrue(isSupportFocusedItemToken(testTrue))
    }

    @Test
    fun test_time() {
        Assert.assertEquals(convertToTime(0), "00:00")
        Assert.assertEquals(convertToTime(60 * 60), "01:00:00")

        Assert.assertEquals(convertToTimeMs(1000 * 60 * 60), "01:00:00")
        Assert.assertEquals(convertToTimeMs(1000 * 60), "01:00")
        Assert.assertEquals(convertToTimeMs(0), "00:00")
    }

    @Test
    fun test_genColor() {
        val resources = (ApplicationProvider.getApplicationContext() as Context).resources
        resources.genColor(R.color.nugu_rounded_button_shape_border_color)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP_MR1])
    fun test_genColor_22() {
        val resources = (ApplicationProvider.getApplicationContext() as Context).resources
        resources.genColor(R.color.nugu_rounded_button_shape_border_color)
    }

    @Test
    fun test_getSpannable() {
        Assert.assertEquals(getSpannable("abcd").toString(), "abcd")
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP_MR1])
    fun test_getSpannable_22() {
        Assert.assertEquals(getSpannable("abcd").toString(), "abcd")
    }
}