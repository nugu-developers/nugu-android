package com.skt.nugu.sdk.platform.android.ux.widget

import android.os.Build
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
class NuguSnackbarTest {

    private lateinit var nuguSnackbar: NuguSnackbar

    @Mock
    private lateinit var parentView: ViewGroup

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        nuguSnackbar = NuguSnackbar(parentView)
    }

    @Test
    fun testException() {
        assertThrows("message resId necessary", IllegalArgumentException::class.java) {
            nuguSnackbar.show()
        }
    }
}