package com.skt.nugu.sdk.core.capabilityagents.playbackcontrol

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.skt.nugu.sdk.core.interfaces.playback.PlaybackButton
import com.skt.nugu.sdk.core.capabilityagents.playbackcontroller.PlaybackController
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import org.junit.Test

class PlaybackControllerTest {
    private val mockContextManager: ContextManagerInterface = mock()
    private val mockMessageSender: MessageSender = mock()

    private val playbackController: PlaybackController = PlaybackController(mockContextManager, mockMessageSender)

    @Test
    fun playButtonPressed() {
        whenever(mockContextManager.getContext(playbackController)).then { playbackController.onContextAvailable("{}") }
        playbackController.onButtonPressed(PlaybackButton.PLAY)
        verify(mockContextManager).getContext(playbackController)
        verify(mockMessageSender).sendMessage(any())
    }
}