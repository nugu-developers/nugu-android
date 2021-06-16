package com.skt.nugu.sdk.platform.android.ux.template.controller

import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateFragment
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock

class BasicTemplateHandlerTest {
    @Mock
    private lateinit var templateFragment: TemplateFragment

    private lateinit var basicTemplateHandler: BasicTemplateHandler

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        basicTemplateHandler = BasicTemplateHandler(mock(), mock(), templateFragment)
    }

    @Test
    fun onCloseClicked() {
        basicTemplateHandler.onCloseClicked()
        verify(templateFragment).close()
    }
}