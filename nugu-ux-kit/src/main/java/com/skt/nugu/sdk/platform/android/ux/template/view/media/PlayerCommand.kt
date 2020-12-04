package com.skt.nugu.sdk.platform.android.ux.template.view.media

enum class PlayerCommand(val command: String) {
    UNKNOWN("unknown"),
    PLAY("play"),
    STOP("stop"),
    PAUSE("pause"),
    PREV("prev"),
    NEXT("next"),
    SHUFFLE("shuffle"),
    REPEAT("repeat"),
    FAVORITE("favorite");

    companion object {
        fun from(command: String): PlayerCommand {
            return when (command) {
                PLAY.command -> PLAY
                STOP.command -> STOP
                PAUSE.command -> PAUSE
                PREV.command -> PREV
                NEXT.command -> NEXT
                SHUFFLE.command -> SHUFFLE
                REPEAT.command -> REPEAT
                FAVORITE.command -> FAVORITE
                else -> UNKNOWN
            }
        }
    }
}