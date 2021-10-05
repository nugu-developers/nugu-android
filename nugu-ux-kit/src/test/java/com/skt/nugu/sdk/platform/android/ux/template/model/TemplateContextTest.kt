package com.skt.nugu.sdk.platform.android.ux.template.model

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class TemplateContextTest{

    @Test
    fun context(){
        val contextString = "{\"focusedItemToken\" : \"token\",\n" +
                "\"visibleTokenList\" : [\"token1\", \"token2\"],\n" +
                "\"lyricsVisible\" : \"true\"}"

        val context = Gson().fromJson(contextString, TemplateContext::class.java)

        assertEquals(context.focusedItemToken, "token")
        assertEquals(context.visibleTokenList!!.size , 2)
        assertEquals(context.lyricsVisible, true)

    }

    @Test
    fun defaultModels(){
        val context = TemplateContext(null, null, null)

        assertNull(context.lyricsVisible)
        assertNull(context.focusedItemToken)
        assertNull(context.visibleTokenList)
    }
}

