package com.skt.nugu.sdk.agent.microphone

/**
 * Provide a interface
 * * Manipulate microphone on/off state
 * * Get of/off state
 */
interface Microphone {
    data class Settings(
        /**
         * on/off state, true: on
         */
        var onOff: Boolean
    )
    
    interface OnSettingChangeListener {
        /**
         * Called when settings changed
         * @param settings settings of the microphone
         */
        fun onSettingsChanged(settings: Settings)
    }

    /**
     * Return current settings of the microphone
     */
    fun getSettings(): Settings

    /**
     * Turn on microphone.
     * @return true if turn on, otherwise return false
     */
    fun on(): Boolean

    /**
     * Turn off microphone.
     * @return true if turn off, otherwise return false
     */
    fun off(): Boolean

    /**
     * Add listener to notified of settings changes
     */
    fun addListener(listener: OnSettingChangeListener)

    /**
     * Remove listener to notified of settings changes
     */
    fun removeListener(listener: OnSettingChangeListener)
}