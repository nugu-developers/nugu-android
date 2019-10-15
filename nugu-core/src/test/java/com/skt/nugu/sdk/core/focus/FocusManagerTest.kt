package com.skt.nugu.sdk.core.focus

import com.nhaarman.mockito_kotlin.*
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito

class MockActivityTrackerInterface : ActivityTrackerInterface {
    override fun notifyOfActivityUpdates(channelStates: List<Channel.State>) {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

const val INCORRECT_CHANNEL_NAME = "dlstoddmsfhEh"

const val DIALOG_CHANNEL_NAME = "dialog_channel"
const val ALERTS_CHANNEL_NAME = "alerts_channel"
const val CONTENT_CHANNEL_NAME = "content_channel"
const val ANOTHER_CHANNEL_NAME = "another_channel"

const val DIALOG_INTERFACE_NAME = "dialog"
const val ALERTS_INTERFACE_NAME = "alerts"
const val CONTENT_INTERFACE_NAME = "content"
const val ANOTHER_INTERFACE_NAME = "another"

const val DIALOG_CHANNEL_PRIORITY = 100
const val ALERTS_CHANNEL_PRIORITY = 200
const val CONTENT_CHANNEL_PRIORITY = 300

class FocusManagerTest : FocusChangeManager() {
    private val focusManager: FocusManager

    private val dialogClient = TestClient()
    private val anotherDialogClient = TestClient()
    private val alertsClient = TestClient()
    private val contentClient = TestClient()

    private val activityTrackerInterface: ActivityTrackerInterface = mock()

    init {
        focusManager = FocusManager(
            listOf(
                FocusManagerInterface.ChannelConfiguration(DIALOG_CHANNEL_NAME, DIALOG_CHANNEL_PRIORITY),
                FocusManagerInterface.ChannelConfiguration(ALERTS_CHANNEL_NAME, ALERTS_CHANNEL_PRIORITY),
                FocusManagerInterface.ChannelConfiguration(CONTENT_CHANNEL_NAME, CONTENT_CHANNEL_PRIORITY)
            ), activityTrackerInterface
        )
    }

    @Test
    fun acquireInvalidChannelName() {
        Assert.assertFalse(focusManager.acquireChannel(INCORRECT_CHANNEL_NAME, dialogClient, DIALOG_CHANNEL_NAME))
    }

    @Test
    fun acquireChannelWithNoOtherChannelsActive() {
        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND)
    }

    @Test
    fun acquireLowerPriorityChannelWithOneHigherPriorityChannelTaken() {
        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        Assert.assertTrue(focusManager.acquireChannel(ALERTS_CHANNEL_NAME, alertsClient, ALERTS_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND)
        assertFocusChange(alertsClient, FocusState.BACKGROUND)
    }

    @Test
    fun acquireLowerPriorityChannelWithTwoHigherPriorityChannelTaken() {
        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        Assert.assertTrue(focusManager.acquireChannel(ALERTS_CHANNEL_NAME, alertsClient, ALERTS_INTERFACE_NAME))
        Assert.assertTrue(focusManager.acquireChannel(CONTENT_CHANNEL_NAME, contentClient, CONTENT_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND)
        assertFocusChange(alertsClient, FocusState.BACKGROUND)
        assertFocusChange(contentClient, FocusState.BACKGROUND)
    }

    @Test
    fun acquireHigherPriorityChannelWithOneLowerPriorityChannelTaken() {
        Assert.assertTrue(focusManager.acquireChannel(CONTENT_CHANNEL_NAME, contentClient, CONTENT_INTERFACE_NAME))
        assertFocusChange(contentClient, FocusState.FOREGROUND)

        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(contentClient, FocusState.BACKGROUND)
        assertFocusChange(dialogClient, FocusState.FOREGROUND)
    }

    @Test
    fun kickOutActivityOnSameChannel() {
        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, anotherDialogClient, ANOTHER_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.NONE)
        assertFocusChange(anotherDialogClient, FocusState.FOREGROUND)
    }

    @Test
    fun simpleReleaseChannel() {
        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(focusManager.releaseChannel(DIALOG_CHANNEL_NAME, dialogClient).get())
        assertFocusChange(dialogClient, FocusState.NONE)
    }

    @Test
    fun simpleReleaseChannelWithIncorrectObserver() {
        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertFalse(focusManager.releaseChannel(CONTENT_CHANNEL_NAME, dialogClient).get())
        Assert.assertFalse(focusManager.releaseChannel(DIALOG_CHANNEL_NAME, contentClient).get())

        assertNoFocusChange(dialogClient)
        assertNoFocusChange(contentClient)
    }

    @Test
    fun releaseForegroundChannelWhileBackgroundChannelTaken() {
        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(focusManager.acquireChannel(CONTENT_CHANNEL_NAME, contentClient, CONTENT_INTERFACE_NAME))
        assertFocusChange(contentClient, FocusState.BACKGROUND)

        Assert.assertTrue(focusManager.releaseChannel(DIALOG_CHANNEL_NAME, dialogClient).get())
        assertFocusChange(dialogClient, FocusState.NONE)
        assertFocusChange(contentClient, FocusState.FOREGROUND)
    }

    @Test
    fun simpleNonTargetedStop() {
        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        focusManager.stopForegroundActivity()
        assertFocusChange(dialogClient, FocusState.NONE)
    }

    @Test
    fun threeNonTargetedStopsWithThreeActivitiesHappening() {
        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(focusManager.acquireChannel(ALERTS_CHANNEL_NAME, alertsClient, ALERTS_INTERFACE_NAME))
        assertFocusChange(alertsClient, FocusState.BACKGROUND)

        Assert.assertTrue(focusManager.acquireChannel(CONTENT_CHANNEL_NAME, contentClient, CONTENT_INTERFACE_NAME))
        assertFocusChange(contentClient, FocusState.BACKGROUND)

        focusManager.stopForegroundActivity()
        assertFocusChange(dialogClient, FocusState.NONE)
        assertFocusChange(alertsClient, FocusState.FOREGROUND)

        focusManager.stopForegroundActivity()
        assertFocusChange(alertsClient, FocusState.NONE)
        assertFocusChange(contentClient, FocusState.FOREGROUND)

        focusManager.stopForegroundActivity()
        assertFocusChange(contentClient, FocusState.NONE)
    }

    @Test
    fun stopForegroundActivityAndAcquireDifferentChannel() {
        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        focusManager.stopForegroundActivity()
        assertFocusChange(dialogClient, FocusState.NONE)

        Assert.assertTrue(focusManager.acquireChannel(CONTENT_CHANNEL_NAME, contentClient, CONTENT_INTERFACE_NAME))
        assertFocusChange(contentClient, FocusState.FOREGROUND)
    }

    @Test
    fun releaseBackgroundChannelWhileTwoChannelsTaken() {
        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(focusManager.acquireChannel(CONTENT_CHANNEL_NAME, contentClient, CONTENT_INTERFACE_NAME))
        assertFocusChange(contentClient, FocusState.BACKGROUND)

        Assert.assertTrue(focusManager.releaseChannel(CONTENT_CHANNEL_NAME, contentClient).get())
        assertFocusChange(contentClient, FocusState.NONE)
        assertNoFocusChange(dialogClient)
    }

    @Test
    fun kickOutActivityOnSameChannelWhileOtherChannelActive() {
        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(focusManager.acquireChannel(CONTENT_CHANNEL_NAME, contentClient, CONTENT_INTERFACE_NAME))
        assertFocusChange(contentClient, FocusState.BACKGROUND)

        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, anotherDialogClient, ANOTHER_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.NONE)
        assertFocusChange(anotherDialogClient, FocusState.FOREGROUND)
        assertNoFocusChange(contentClient)
    }

    @Test
    fun addListener() {
        val listeners: MutableList<FocusManagerInterface.OnFocusChangedListener> = ArrayList()
        listeners.add(mock())
        listeners.add(mock())

        for (listener in listeners) {
            focusManager.addListener(listener)
        }

        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND) //  wait focus changed
        for (listener in listeners) {
            verify(listener).onFocusChanged(DIALOG_CHANNEL_NAME, FocusState.FOREGROUND, DIALOG_INTERFACE_NAME)
        }

        Assert.assertTrue(focusManager.acquireChannel(CONTENT_CHANNEL_NAME, contentClient, CONTENT_CHANNEL_NAME))
        assertFocusChange(contentClient, FocusState.BACKGROUND) //  wait focus changed
        for (listener in listeners) {
            verify(listener).onFocusChanged(CONTENT_CHANNEL_NAME, FocusState.BACKGROUND, CONTENT_CHANNEL_NAME)
        }

        focusManager.stopForegroundActivity()
        assertFocusChange(dialogClient, FocusState.NONE) //  wait focus changed
        assertFocusChange(contentClient, FocusState.FOREGROUND) //  wait focus changed
        for (listener in listeners) {
            verify(listener).onFocusChanged(DIALOG_CHANNEL_NAME, FocusState.NONE, DIALOG_INTERFACE_NAME)
            verify(listener).onFocusChanged(CONTENT_CHANNEL_NAME, FocusState.FOREGROUND, CONTENT_CHANNEL_NAME)
        }
    }

    @Test
    fun removeListener() {
        val listeners: MutableList<FocusManagerInterface.OnFocusChangedListener> = ArrayList()
        listeners.add(mock())
        listeners.add(mock())

        for (listener in listeners) {
            focusManager.addListener(listener)
        }

        val activeListeners = listeners

        Assert.assertTrue(focusManager.acquireChannel(DIALOG_CHANNEL_NAME, dialogClient, DIALOG_INTERFACE_NAME))
        assertFocusChange(dialogClient, FocusState.FOREGROUND) //  wait focus changed
        for (listener in listeners) {
            verify(listener).onFocusChanged(DIALOG_CHANNEL_NAME, FocusState.FOREGROUND, DIALOG_INTERFACE_NAME)
        }

        focusManager.removeListener(activeListeners.removeAt(0))

        Assert.assertTrue(focusManager.acquireChannel(CONTENT_CHANNEL_NAME, contentClient, CONTENT_CHANNEL_NAME))
        assertFocusChange(contentClient, FocusState.BACKGROUND) //  wait focus changed
        for (listener in activeListeners) {
            verify(listener).onFocusChanged(CONTENT_CHANNEL_NAME, FocusState.BACKGROUND, CONTENT_CHANNEL_NAME)
        }

        while (activeListeners.isNotEmpty()) {
            focusManager.removeListener(activeListeners.removeAt(0))
        }

        focusManager.stopForegroundActivity()
    }

    @Test
    fun activityTracker() {
        whenever(activityTrackerInterface.notifyOfActivityUpdates(Mockito.anyList())).then {
            val param = it.arguments[0]
            Assert.assertFalse(param == null)
            Assert.assertTrue(param is List<*>)
            val states = param as List<Channel.State>
            Assert.assertEquals(1, states.size)
            val state = states[0]
            Assert.assertTrue(CONTENT_CHANNEL_NAME == state.name)
            Assert.assertTrue(FocusState.FOREGROUND == state.focusState)
            Assert.assertTrue(CONTENT_INTERFACE_NAME == state.interfaceName)
            any()
        }

        Assert.assertTrue(focusManager.acquireChannel(CONTENT_CHANNEL_NAME, contentClient, CONTENT_INTERFACE_NAME))
        assertFocusChange(contentClient, FocusState.FOREGROUND)

        verify(activityTrackerInterface).notifyOfActivityUpdates(any())
    }
}