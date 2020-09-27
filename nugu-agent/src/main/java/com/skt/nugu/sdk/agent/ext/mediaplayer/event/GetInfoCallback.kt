package com.skt.nugu.sdk.agent.ext.mediaplayer.event

import com.skt.nugu.sdk.agent.ext.mediaplayer.Song

interface GetInfoCallback {
    fun onSuccess(song: Song?, issueDate: String?, playTime: String?, playListName: String?)
    fun onFailure(errorCode: String?)
}