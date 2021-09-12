package com.skt.nugu.sdk.platform.android.ux.widget

import android.os.Build
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skt.nugu.sdk.agent.chips.Chip
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], manifest = Config.NONE)
class NuguChipsViewTest {

    private val nuguChipsView = NuguChipsView(ApplicationProvider.getApplicationContext())

    @Test
    fun testScrollListener() {
        val recyclerViewMock = mock(RecyclerView::class.java)

        // call onScrolled when listener's absence
        nuguChipsView.onScrollListener.onScrolled(recyclerViewMock, 0, 0)

        // set listener
        val chipsListener: NuguChipsView.OnChipsListener = mock()
        nuguChipsView.setOnChipsListener(chipsListener)

        // call onScrolled when listener exists
        nuguChipsView.onScrollListener.onScrolled(recyclerViewMock, 0, 0)
        verify(chipsListener).onScrolled(0, 0)
    }

    @Test
    fun testAdapter(){
        val adapterMock : NuguChipsView.AdapterChips = mock()
        nuguChipsView.adapter = adapterMock

        val chipsItem = NuguChipsView.Item(text = "request", Chip.Type.GENERAL)

        // add
        nuguChipsView.addItem(chipsItem)
        verify(adapterMock).notifyDataSetChanged()
        verify(adapterMock).startNudgeAnimationIfNeeded()

        // remove all
        nuguChipsView.removeAll()
        verify(adapterMock, times(2)).notifyDataSetChanged()
        verify(adapterMock).stopNudgeAnimationIfNeeded()

        // on voiceChrome hidden
        nuguChipsView.onVoiceChromeHidden()
        verify(adapterMock, times(2)).stopNudgeAnimationIfNeeded()
    }
}