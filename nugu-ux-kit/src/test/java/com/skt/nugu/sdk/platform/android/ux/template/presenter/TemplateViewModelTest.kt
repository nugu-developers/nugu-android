package com.skt.nugu.sdk.platform.android.ux.template.presenter

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateViewModelTest {

    private var template = "{\"param1\":\"value1\"}"
    private var updatedTemplate = "{\"param1\":\"value1-1\"}"
    private var expectedResult = "{\"param1\":\"value1-1\"}"

    @Test
    fun mergeTemplate() {
        assertEquals(mergeTemplate(template, updatedTemplate), expectedResult)

        template = "{\"param1\":\"value1\",\"param2\":\"value2\"}"
        updatedTemplate = "{\"param2\":\"value2-2\"}"
        expectedResult = "{\"param1\":\"value1\",\"param2\":\"value2-2\"}"
        assertEquals(mergeTemplate(template, updatedTemplate), expectedResult)

        template = "{\"param1\":\"value1\",\"list\":[{\"listItem1\":\"list-value-1\"},{\"listItem2\":\"list-value-2\"}]}"
        updatedTemplate = ""
        expectedResult = "{\"param1\":\"value1\",\"list\":[{\"listItem1\":\"list-value-1\"},{\"listItem2\":\"list-value-2\"}]}"
        assertEquals(mergeTemplate(template, updatedTemplate), expectedResult)

        template = "{\"param1\":\"value1\",\"list\":[{\"listItem1\":\"list-value-1\"},{\"listItem2\":\"list-value-2\"}]}"
        updatedTemplate = "{\"param2\":\"value2-2\"}"
        expectedResult = "{\"param1\":\"value1\",\"list\":[{\"listItem1\":\"list-value-1\"},{\"listItem2\":\"list-value-2\"}],\"param2\":\"value2-2\"}"
        assertEquals(mergeTemplate(template, updatedTemplate), expectedResult)
    }
}