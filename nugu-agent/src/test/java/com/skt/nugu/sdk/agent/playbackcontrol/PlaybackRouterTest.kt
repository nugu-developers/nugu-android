package com.skt.nugu.sdk.agent.playbackcontrol

import org.mockito.kotlin.*
import com.skt.nugu.sdk.agent.playback.PlaybackButton
import com.skt.nugu.sdk.agent.playback.PlaybackHandler
import com.skt.nugu.sdk.agent.playback.impl.PlaybackRouter
import org.junit.Test

class PlaybackRouterTest {
    private val defaultPlaybackHandler: PlaybackHandler = mock()
    private val playbackRouter =
        PlaybackRouter()//PlaybackRouter(defaultPlaybackHandler)
    private val secondPlaybackHandler: PlaybackHandler = mock()

    @Test
    fun defaultHandler() {
        playbackRouter.setHandler(defaultPlaybackHandler)
        for(button in PlaybackButton.values()) {
            playbackRouter.buttonPressed(button)
            verify(defaultPlaybackHandler).onButtonPressed(button)
        }

        /*
        for(toggle in PlaybackToggle.values()) {
            playbackRouter.togglePressed(toggle, true)
            verify(defaultPlaybackHandler).onTogglePressed(toggle, true)
        }
        */
    }

    @Test
    fun secondHandler() {
        playbackRouter.setHandler(secondPlaybackHandler)

        for(button in PlaybackButton.values()) {
            playbackRouter.buttonPressed(button)
            verify(secondPlaybackHandler).onButtonPressed(button)
        }

        /*
        for(toggle in PlaybackToggle.values()) {
            playbackRouter.togglePressed(toggle, true)
            verify(secondPlaybackHandler).onTogglePressed(toggle, true)
        }
        */
    }

    /*
    @Test
    fun switchToDefaultHandler() {
        playbackRouter.setHandler(secondPlaybackHandler)

        playbackRouter.buttonPressed(PlaybackButton.PLAY)
        verify(secondPlaybackHandler).onButtonPressed(PlaybackButton.PLAY)

        playbackRouter.togglePressed(PlaybackToggle.SHUFFLE, true)
        verify(secondPlaybackHandler).onTogglePressed(PlaybackToggle.SHUFFLE, true)

        playbackRouter.switchToDefaultHandler()

        for(button in PlaybackButton.values()) {
            playbackRouter.buttonPressed(button)
            verify(defaultPlaybackHandler).onButtonPressed(button)
        }

        for(toggle in PlaybackToggle.values()) {
            playbackRouter.togglePressed(toggle, true)
            verify(defaultPlaybackHandler).onTogglePressed(toggle, true)
        }
    }
    */
}