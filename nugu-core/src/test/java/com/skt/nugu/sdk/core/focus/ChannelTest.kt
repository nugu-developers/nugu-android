package com.skt.nugu.sdk.core.focus

import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import org.junit.Assert
import org.junit.Test

class ChannelTest : FocusChangeManager() {
    private val clientA = TestClient()
    private val clientB = TestClient()
    private val testChannel = Channel(DIALOG_CHANNEL_NAME, Channel.Priority(DIALOG_CHANNEL_PRIORITY, DIALOG_CHANNEL_PRIORITY))

    @Test
    fun getName() {
        Assert.assertEquals(testChannel.name, DIALOG_CHANNEL_NAME)
    }

    @Test
    fun getPriority() {
        Assert.assertEquals(testChannel.priority.acquire, DIALOG_CHANNEL_PRIORITY)
        Assert.assertEquals(testChannel.priority.release, DIALOG_CHANNEL_PRIORITY)
    }

    @Test
    fun setObserverThenSetFocus() {
        testChannel.setObserver(clientA)

        Assert.assertTrue(testChannel.setFocus(FocusState.FOREGROUND))
        assertFocusChange(clientA, FocusState.FOREGROUND)

        Assert.assertTrue(testChannel.setFocus(FocusState.BACKGROUND))
        assertFocusChange(clientA, FocusState.BACKGROUND)

        Assert.assertTrue(testChannel.setFocus(FocusState.NONE))
        assertFocusChange(clientA, FocusState.NONE)

        Assert.assertFalse(testChannel.setFocus(FocusState.NONE))
    }

    @Test
    fun hasObserver() {
        Assert.assertFalse(testChannel.hasObserver())
        testChannel.setObserver(clientA)
        Assert.assertTrue(testChannel.hasObserver())
        testChannel.setObserver(clientB)
        Assert.assertTrue(testChannel.hasObserver())
        testChannel.setObserver(null)
        Assert.assertFalse(testChannel.hasObserver())
    }
}