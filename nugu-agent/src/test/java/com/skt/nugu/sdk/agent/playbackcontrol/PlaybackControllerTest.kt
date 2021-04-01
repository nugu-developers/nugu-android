package com.skt.nugu.sdk.agent.playbackcontrol

import com.nhaarman.mockito_kotlin.*
import com.skt.nugu.sdk.agent.playback.PlaybackButton
import com.skt.nugu.sdk.agent.playback.impl.PlaybackController
import com.skt.nugu.sdk.core.interfaces.context.ContextManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import org.junit.Test

class PlaybackControllerTest {
    private val mockContextManager: ContextManagerInterface = mock()
    private val mockMessageSender: MessageSender = mock()

    private val playbackController: PlaybackController =
        PlaybackController(
            mockContextManager,
            mockMessageSender
        )

    @Test
    fun playButtonPressed() {
        whenever(mockContextManager.getContext(playbackController)).then { playbackController.onContextAvailable("{}") }
        playbackController.onButtonPressed(PlaybackButton.PLAY)
        verify(mockContextManager).getContext(playbackController)
        verify(mockMessageSender).newCall(any(), eq(null))
    }
}