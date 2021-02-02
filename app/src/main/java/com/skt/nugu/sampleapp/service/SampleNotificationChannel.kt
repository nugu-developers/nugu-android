package com.skt.nugu.sampleapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.annotation.RequiresApi


enum class SampleNotificationChannel(
    val channelId: String,
    private val channelName: String,
    private val channelImportance: Int = NotificationManager.IMPORTANCE_DEFAULT
) {
    MUSIC("channel_0", //The id of the channel. Must be unique per package.
        "Music Player Channel", //The user visible name of the channel
        NotificationManager.IMPORTANCE_LOW),

    SAMPLE("sample",
        "sample app"
    );

    @RequiresApi(Build.VERSION_CODES.O)
    fun createChannel() = NotificationChannel(channelId, channelName, channelImportance).apply {
        setSound(null, null)
    }
}