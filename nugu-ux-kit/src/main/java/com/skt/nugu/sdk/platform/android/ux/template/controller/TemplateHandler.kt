package com.skt.nugu.sdk.platform.android.ux.template.controller

import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface

/**
 * Basically Template renders figures to inform. And control logic exists on client side.
 * So client state must to be sent to Template for updating figures
 * and the event like selection of items in Template must be noticed to client side to be handled.
 * And Template could directly requests specific action to client side.
 * This class do these things.
 */
interface TemplateHandler {

    // template -> client side
    fun onElementSelected(tokenId: String)

    fun onChipSelected(text: String)

    fun onCloseClicked()

    fun onNuguButtonSelected()

    fun onContextChanged(context: String)

    fun onPlayerCommand(command: String, param: String = "")

    fun showToast(text: String)

    fun showActivity(className: String)

    fun playTTS(text: String)

    // client side -> template (There are only events about media state for now)
    fun setClientEventListener(listener: ClientEventListener) {}

    interface ClientEventListener {
        fun onMediaStateChanged(activity: AudioPlayerAgentInterface.State, currentTimeMs: Long, currentProgress: Float) {}

        fun onMediaDurationRetrieved(durationMs: Long) {}

        fun onMediaProgressChanged(progress: Float, currentTimeMs: Long) {}
    }

    fun clear()
}