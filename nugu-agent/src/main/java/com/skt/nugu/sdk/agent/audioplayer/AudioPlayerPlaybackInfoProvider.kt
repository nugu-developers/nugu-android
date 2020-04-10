package com.skt.nugu.sdk.agent.audioplayer

interface AudioPlayerPlaybackInfoProvider {
    fun getCurrentState(): AudioPlayerAgentInterface.State
    fun getToken(): String?
    fun getOffsetInMilliseconds(): Long?
    fun getPlayServiceId(): String?
}