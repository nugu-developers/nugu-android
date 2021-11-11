package com.skt.nugu.sdk.platform.android.ux.template.controller

import androidx.fragment.app.Fragment
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateFragment
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class DefaultTemplateHandlerTest {
    @Mock
    private lateinit var templateFragment: TemplateFragment

    @Mock
    private lateinit var fragment: Fragment

    @Mock
    private lateinit var nuguAndroidClient: NuguAndroidClient

    private lateinit var templateHandler: DefaultTemplateHandler

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        templateHandler = DefaultTemplateHandler(mock(), mock(), templateFragment)

        whenever(templateHandler.getNuguClient()).thenReturn(nuguAndroidClient)
    }

    @Test
    fun onCloseClicked() {
        templateHandler.onCloseClicked()
        verify(templateFragment).close()

        templateHandler.clear()

        templateHandler.onCloseClicked()
        verifyNoMoreInteractions(templateFragment)

        templateHandler.updateFragment(fragment)
        templateHandler.onCloseClicked()
        verifyNoMoreInteractions(templateFragment)
    }

    @Test
    fun onCloseAllClicked() {
        templateHandler.onCloseAllClicked()
        verify(templateFragment).closeAll()
    }

    @Test
    fun onCloseWithParent(){
        templateHandler.onCloseWithParents()
        verify(templateFragment).closeWithParents()
    }

    @Test
    fun showToast(){
        try {
            templateHandler.showToast("toast")
        }catch(e: Exception){
            e.printStackTrace()
        }

        templateHandler.clear()
        templateHandler.showToast("toast")
        verify(templateFragment).requireContext()
//        verifyNoMoreInteractions(templateFragment)
    }

    @Test
    fun showActivity() {
        try {
            templateHandler.showActivity("MainActivity")
        }catch(e: Exception){
            e.printStackTrace()
        }

        templateHandler.clear()
        templateHandler.showActivity("MainActivity")
//        verify(templateFragment).startActivity(ArgumentMatchers.any())
    }

}