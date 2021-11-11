package com.skt.nugu.sdk.platform.android.ux.template.controller

import androidx.fragment.app.Fragment
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class TemplateHandlerTest{

    @Mock
    lateinit var clientProvider : TemplateRenderer.NuguClientProvider

    @Mock
    lateinit var templateInfo : TemplateHandler.TemplateInfo

    @Mock
    lateinit var fragment : Fragment

    @Before
    fun setup(){
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun defaultHandler(){
        val handler = TemplateHandler.TemplateHandlerFactory().onCreate(clientProvider, templateInfo, fragment)
        assertTrue(handler is DefaultTemplateHandler)
    }
}