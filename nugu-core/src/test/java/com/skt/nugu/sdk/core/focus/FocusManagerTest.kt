package com.skt.nugu.sdk.core.focus

import org.mockito.kotlin.*
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.interfaces.log.LogInterface
import com.skt.nugu.sdk.core.utils.Logger
import org.junit.Assert
import org.junit.Test

const val INCORRECT_CHANNEL_NAME = "dlstoddmsfhEh"

const val DIALOG_CHANNEL_NAME = "dialog_channel"
const val ALERTS_CHANNEL_NAME = "alerts_channel"
const val CONTENT_CHANNEL_NAME = "content_channel"
const val INTERACT_CHANNEL_NAME = "interact_channel"
const val ANOTHER_CHANNEL_NAME = "another_channel"

const val DIALOG_INTERFACE_NAME = "dialog"
const val ALERTS_INTERFACE_NAME = "alerts"
const val CONTENT_INTERFACE_NAME = "content"
const val INTERACT_INTERFACE_NAME = "interact"
const val ANOTHER_INTERFACE_NAME = "another"

const val INTERACT_ACQUIRE_PRIORITY = 50
const val INTERACT_RELEASE_PRIORITY = 900
const val DIALOG_CHANNEL_PRIORITY = 100
const val ALERTS_CHANNEL_PRIORITY = 200
const val CONTENT_CHANNEL_PRIORITY = 300

class FocusManagerTest : FocusChangeManager() {
    private val focusManager: FocusManager

    private val dialogClient = TestClient()
    private val anotherDialogClient = TestClient()
    private val alertsClient = TestClient()
    private val interactClient = TestClient()
    private val contentClient = TestClient()

    private val dialogChannelConfiguration = FocusManagerInterface.ChannelConfiguration(
        DIALOG_CHANNEL_NAME,
        DIALOG_CHANNEL_PRIORITY,
        DIALOG_CHANNEL_PRIORITY
    )

    private val alertsChannelConfiguration = FocusManagerInterface.ChannelConfiguration(
        ALERTS_CHANNEL_NAME,
        ALERTS_CHANNEL_PRIORITY,
        ALERTS_CHANNEL_PRIORITY
    )

    private val contentChannelConfiguration = FocusManagerInterface.ChannelConfiguration(
        CONTENT_CHANNEL_NAME,
        CONTENT_CHANNEL_PRIORITY,
        CONTENT_CHANNEL_PRIORITY
    )

    private val interactChannelConfiguration = FocusManagerInterface.ChannelConfiguration(
        INTERACT_CHANNEL_NAME,
        INTERACT_ACQUIRE_PRIORITY,
        INTERACT_RELEASE_PRIORITY
    )

    init {
        Logger.logger = object : LogInterface {
            override fun d(tag: String, msg: String, throwable: Throwable?) {
                System.out.println("$tag - $msg")
            }

            override fun e(tag: String, msg: String, throwable: Throwable?) {
            }

            override fun w(tag: String, msg: String, throwable: Throwable?) {
            }

            override fun i(tag: String, msg: String, throwable: Throwable?) {
            }
        }
        focusManager = FocusManager(
            listOf(
                dialogChannelConfiguration,
                alertsChannelConfiguration,
                contentChannelConfiguration,
                interactChannelConfiguration
            )
        )
    }

    @Test
    fun acquireInvalidChannelName() {
        Assert.assertFalse(
            focusManager.acquireChannel(
                INCORRECT_CHANNEL_NAME,
                dialogClient,
                DIALOG_CHANNEL_NAME
            )
        )
    }

    @Test
    fun acquireChannelWithNoOtherChannelsActive() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)
    }

    @Test
    fun acquireLowerPriorityChannelWithOneHigherPriorityChannelTaken() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        Assert.assertTrue(
            focusManager.acquireChannel(
                ALERTS_CHANNEL_NAME,
                alertsClient,
                ALERTS_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)
        assertFocusChange(alertsClient, FocusState.BACKGROUND)
    }

    @Test
    fun acquireLowerPriorityChannelWithTwoHigherPriorityChannelTaken() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        Assert.assertTrue(
            focusManager.acquireChannel(
                ALERTS_CHANNEL_NAME,
                alertsClient,
                ALERTS_INTERFACE_NAME
            )
        )
        Assert.assertTrue(
            focusManager.acquireChannel(
                CONTENT_CHANNEL_NAME,
                contentClient,
                CONTENT_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)
        assertFocusChange(alertsClient, FocusState.BACKGROUND)
        assertFocusChange(contentClient, FocusState.BACKGROUND)
    }

    @Test
    fun acquireHigherPriorityChannelWithOneLowerPriorityChannelTaken() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                CONTENT_CHANNEL_NAME,
                contentClient,
                CONTENT_INTERFACE_NAME
            )
        )
        assertFocusChange(contentClient, FocusState.FOREGROUND)

        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(contentClient, FocusState.BACKGROUND)
        assertFocusChange(dialogClient, FocusState.FOREGROUND)
    }

    @Test
    fun acquireHigherPriorityChannelWithLowerForegroundAndHigherBackgroundChannelTaken() {

        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(
            focusManager.acquireChannel(
                INTERACT_CHANNEL_NAME,
                interactClient,
                INTERACT_INTERFACE_NAME
            )
        )

        Assert.assertTrue(
            focusManager.acquireChannel(
                CONTENT_CHANNEL_NAME,
                contentClient,
                CONTENT_INTERFACE_NAME
            )
        )
        assertFocusChange(contentClient, FocusState.BACKGROUND)
        assertFocusChange(interactClient, FocusState.FOREGROUND)
        assertFocusChange(dialogClient, FocusState.BACKGROUND)
    }

    @Test
    fun kickOutActivityOnSameChannel() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                anotherDialogClient,
                ANOTHER_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.NONE)
        assertFocusChange(anotherDialogClient, FocusState.FOREGROUND)
    }

    @Test
    fun simpleReleaseChannel() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(focusManager.releaseChannel(DIALOG_CHANNEL_NAME, dialogClient).get())
        assertFocusChange(dialogClient, FocusState.NONE)
    }

    @Test
    fun simpleReleaseChannelWithIncorrectObserver() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertFalse(focusManager.releaseChannel(CONTENT_CHANNEL_NAME, dialogClient).get())
        Assert.assertFalse(focusManager.releaseChannel(DIALOG_CHANNEL_NAME, contentClient).get())

        assertNoFocusChange(dialogClient)
        assertNoFocusChange(contentClient)
    }

    @Test
    fun releaseForegroundChannelWhileBackgroundChannelTaken() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(
            focusManager.acquireChannel(
                CONTENT_CHANNEL_NAME,
                contentClient,
                CONTENT_INTERFACE_NAME
            )
        )
        assertFocusChange(contentClient, FocusState.BACKGROUND)

        Assert.assertTrue(focusManager.releaseChannel(DIALOG_CHANNEL_NAME, dialogClient).get())
        assertFocusChange(dialogClient, FocusState.NONE)
        assertFocusChange(contentClient, FocusState.FOREGROUND)
    }

    @Test
    fun simpleNonTargetedStop() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        focusManager.releaseChannel(
            DIALOG_CHANNEL_NAME,
            dialogClient
        )
        assertFocusChange(dialogClient, FocusState.NONE)
    }

    @Test
    fun acquireFocusOnBackgroundAlready() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                ALERTS_CHANNEL_NAME,
                alertsClient,
                ALERTS_INTERFACE_NAME
            )
        )
        assertFocusChange(alertsClient, FocusState.FOREGROUND)

        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)
        assertFocusChange(alertsClient, FocusState.BACKGROUND)

        Assert.assertTrue(
            focusManager.acquireChannel(
                ALERTS_CHANNEL_NAME,
                alertsClient,
                ALERTS_INTERFACE_NAME
            )
        )

        focusManager.releaseChannel(
            DIALOG_CHANNEL_NAME,
            dialogClient
        )
        assertFocusChange(dialogClient, FocusState.NONE)
        assertFocusChange(alertsClient, FocusState.FOREGROUND)
    }

    @Test
    fun threeNonTargetedStopsWithThreeActivitiesHappening() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(
            focusManager.acquireChannel(
                ALERTS_CHANNEL_NAME,
                alertsClient,
                ALERTS_INTERFACE_NAME
            )
        )
        assertFocusChange(alertsClient, FocusState.BACKGROUND)

        Assert.assertTrue(
            focusManager.acquireChannel(
                CONTENT_CHANNEL_NAME,
                contentClient,
                CONTENT_INTERFACE_NAME
            )
        )
        assertFocusChange(contentClient, FocusState.BACKGROUND)

        focusManager.releaseChannel(
            DIALOG_CHANNEL_NAME,
            dialogClient
        )
        assertFocusChange(dialogClient, FocusState.NONE)
        assertFocusChange(alertsClient, FocusState.FOREGROUND)

        focusManager.releaseChannel(
            ALERTS_CHANNEL_NAME,
            alertsClient
        )
        assertFocusChange(alertsClient, FocusState.NONE)
        assertFocusChange(contentClient, FocusState.FOREGROUND)

        focusManager.releaseChannel(
            CONTENT_CHANNEL_NAME,
            contentClient
        )
        assertFocusChange(contentClient, FocusState.NONE)
    }

    @Test
    fun stopForegroundActivityAndAcquireDifferentChannel() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        focusManager.releaseChannel(
            DIALOG_CHANNEL_NAME,
            dialogClient
        )
        assertFocusChange(dialogClient, FocusState.NONE)

        Assert.assertTrue(
            focusManager.acquireChannel(
                CONTENT_CHANNEL_NAME,
                contentClient,
                CONTENT_INTERFACE_NAME
            )
        )
        assertFocusChange(contentClient, FocusState.FOREGROUND)
    }

    @Test
    fun releaseBackgroundChannelWhileTwoChannelsTaken() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(
            focusManager.acquireChannel(
                CONTENT_CHANNEL_NAME,
                contentClient,
                CONTENT_INTERFACE_NAME
            )
        )
        assertFocusChange(contentClient, FocusState.BACKGROUND)

        Assert.assertTrue(focusManager.releaseChannel(CONTENT_CHANNEL_NAME, contentClient).get())
        assertFocusChange(contentClient, FocusState.NONE)
        assertNoFocusChange(dialogClient)
    }

    @Test
    fun kickOutActivityOnSameChannelWhileOtherChannelActive() {
        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND)

        Assert.assertTrue(
            focusManager.acquireChannel(
                CONTENT_CHANNEL_NAME,
                contentClient,
                CONTENT_INTERFACE_NAME
            )
        )
        assertFocusChange(contentClient, FocusState.BACKGROUND)

        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                anotherDialogClient,
                ANOTHER_INTERFACE_NAME
            )
        )
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

        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND) //  wait focus changed
        for (listener in listeners) {
            verify(listener).onFocusChanged(
                dialogChannelConfiguration,
                FocusState.FOREGROUND,
                DIALOG_INTERFACE_NAME
            )
        }

        Assert.assertTrue(
            focusManager.acquireChannel(
                CONTENT_CHANNEL_NAME,
                contentClient,
                CONTENT_CHANNEL_NAME
            )
        )
        assertFocusChange(contentClient, FocusState.BACKGROUND) //  wait focus changed
        for (listener in listeners) {
            verify(listener).onFocusChanged(
                contentChannelConfiguration,
                FocusState.BACKGROUND,
                CONTENT_CHANNEL_NAME
            )
        }

        focusManager.releaseChannel(
            DIALOG_CHANNEL_NAME,
            dialogClient
        )
        assertFocusChange(dialogClient, FocusState.NONE) //  wait focus changed
        assertFocusChange(contentClient, FocusState.FOREGROUND) //  wait focus changed
        for (listener in listeners) {
            verify(listener).onFocusChanged(
                dialogChannelConfiguration,
                FocusState.NONE,
                DIALOG_INTERFACE_NAME
            )
            verify(listener).onFocusChanged(
                contentChannelConfiguration,
                FocusState.FOREGROUND,
                CONTENT_CHANNEL_NAME
            )
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

        Assert.assertTrue(
            focusManager.acquireChannel(
                DIALOG_CHANNEL_NAME,
                dialogClient,
                DIALOG_INTERFACE_NAME
            )
        )
        assertFocusChange(dialogClient, FocusState.FOREGROUND) //  wait focus changed
        for (listener in listeners) {
            verify(listener).onFocusChanged(
                dialogChannelConfiguration,
                FocusState.FOREGROUND,
                DIALOG_INTERFACE_NAME
            )
        }

        focusManager.removeListener(activeListeners.removeAt(0))

        Assert.assertTrue(
            focusManager.acquireChannel(
                CONTENT_CHANNEL_NAME,
                contentClient,
                CONTENT_CHANNEL_NAME
            )
        )
        assertFocusChange(contentClient, FocusState.BACKGROUND) //  wait focus changed
        for (listener in activeListeners) {
            verify(listener).onFocusChanged(
                contentChannelConfiguration, FocusState.BACKGROUND, CONTENT_CHANNEL_NAME
            )
        }

        while (activeListeners.isNotEmpty()) {
            focusManager.removeListener(activeListeners.removeAt(0))
        }
    }
}