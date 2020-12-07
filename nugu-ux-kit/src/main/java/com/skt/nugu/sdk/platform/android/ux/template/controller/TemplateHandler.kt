package com.skt.nugu.sdk.platform.android.ux.template.controller

import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.agent.common.Direction

/**
 * Basically Template renders figures to inform. And control logic exists on client side.
 * Both side could notify there updated state and directly request specific action to other side.
 * This class is interface for these things to be done.
 */
interface TemplateHandler {

    // template -> client side
    fun onElementSelected(tokenId: String)

    fun onChipSelected(text: String)

    fun onCloseClicked()

    fun onNuguButtonSelected()

    fun onPlayerCommand(command: String, param: String = "")

    fun onContextChanged(context: String)

    fun onControlResult(action: String, result: String)

    fun showToast(text: String)

    fun showActivity(className: String)

    fun playTTS(text: String)

    // client side -> template
    fun setClientListener(listener: ClientListener) {}

    interface ClientListener {
        fun onMediaStateChanged(activity: AudioPlayerAgentInterface.State, currentTimeMs: Long, currentProgress: Float) {}

        fun onMediaDurationRetrieved(durationMs: Long) {}

        fun onMediaProgressChanged(progress: Float, currentTimeMs: Long) {}

        fun controlFocus(direction: Direction): Boolean = false

        fun controlScroll(direction: Direction): Boolean = false

        fun getFocusedItemToken(): String? = null

        fun getVisibleTokenList(): List<String>? = null
    }

    fun clear()
}