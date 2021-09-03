package com.skt.nugu.sdk.platform.android.ux.template.controller

import androidx.fragment.app.Fragment
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateFragment
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class BasicTemplateHandlerTest {
    @Mock
    private lateinit var templateFragment: TemplateFragment

    @Mock
    private lateinit var fragment: Fragment

    @Mock
    private lateinit var nuguAndroidClient: NuguAndroidClient

    private lateinit var basicTemplateHandler: BasicTemplateHandler

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        basicTemplateHandler = BasicTemplateHandler(mock(), mock(), templateFragment)

        whenever(basicTemplateHandler.getNuguClient()).thenReturn(nuguAndroidClient)
    }

    @Test
    fun onCloseClicked() {
        basicTemplateHandler.onCloseClicked()
        verify(templateFragment).close()

        basicTemplateHandler.clear()

        basicTemplateHandler.onCloseClicked()
        verifyNoMoreInteractions(templateFragment)

        basicTemplateHandler.updateFragment(fragment)
        basicTemplateHandler.onCloseClicked()
        verifyNoMoreInteractions(templateFragment)
    }

    @Test
    fun onCloseAllClicked() {
        basicTemplateHandler.onCloseAllClicked()
        verify(templateFragment).closeAll()
    }

    @Test
    fun showToast(){
        try {
            basicTemplateHandler.showToast("toast")
        }catch(e: Exception){
            e.printStackTrace()
        }

        basicTemplateHandler.clear()
        basicTemplateHandler.showToast("toast")
        verify(templateFragment).requireContext()
//        verifyNoMoreInteractions(templateFragment)
    }

    @Test
    fun showActivity() {
        try {
            basicTemplateHandler.showActivity("MainActivity")
        }catch(e: Exception){
            e.printStackTrace()
        }

        basicTemplateHandler.clear()
        basicTemplateHandler.showActivity("MainActivity")
//        verify(templateFragment).startActivity(ArgumentMatchers.any())
    }
}