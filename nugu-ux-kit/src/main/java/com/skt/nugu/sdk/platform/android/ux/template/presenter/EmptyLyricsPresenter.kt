package com.skt.nugu.sdk.platform.android.ux.template.presenter

import com.skt.nugu.sdk.agent.audioplayer.lyrics.LyricsPresenter
import com.skt.nugu.sdk.agent.common.Direction

object EmptyLyricsPresenter : LyricsPresenter {
    override fun getVisibility(): Boolean {
        return false
    }

    override fun show(): Boolean {
        return false
    }

    override fun hide(): Boolean {
        return false
    }

    override fun controlPage(direction: Direction): Boolean {
        return false
    }
}