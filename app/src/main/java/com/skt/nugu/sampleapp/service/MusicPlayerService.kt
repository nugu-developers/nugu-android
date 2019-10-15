/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skt.nugu.sampleapp.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.NotificationTarget
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.playback.PlaybackButton
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.application.SampleApplication
import com.skt.nugu.sampleapp.client.ClientManager
import org.json.JSONObject

class MusicPlayerService : Service(), AudioPlayerAgentInterface.Listener {
    companion object {
        private const val TAG = "MusicPlayerService"

        private const val SERVICE_ID = 1

        private const val EXTRA_AUDIO_ITEM_ID = "extra_audio_item_id"
        private const val EXTRA_AUDIO_ITEM_TEMPLATE = "extra_audio_item_template"
        private const val EXTRA_AUDIO_ITEM_OFFSET = "extra_audio_item_offset"

        private const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        private const val ACTION_PLAY = "ACTION_PLAY"
        private const val ACTION_PREV = "ACTION_PREV"
        private const val ACTION_NEXT = "ACTION_NEXT"
        private const val ACTION_STOP = "ACTION_STOP"

        fun startService(context: Context, audioItemContext: AudioPlayerAgentInterface.Context) {
            val intent = Intent(context.applicationContext, MusicPlayerService::class.java).apply {
                action = ACTION_START_SERVICE
                putExtra(EXTRA_AUDIO_ITEM_ID, audioItemContext.audioItemId)
                putExtra(EXTRA_AUDIO_ITEM_TEMPLATE, audioItemContext.audioItemTemplate)
                putExtra(EXTRA_AUDIO_ITEM_OFFSET, audioItemContext.offset)
            }

            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context.applicationContext, MusicPlayerService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
        }
    }

    private var playerActivity: AudioPlayerAgentInterface.State = AudioPlayerAgentInterface.State.PLAYING
    private var audioItemContext: AudioPlayerAgentInterface.Context? = null
    private var isDestroying: Boolean = false
    private var handler: Handler = Handler()
    private val stopServiceRunnable = Runnable {
        stopServiceSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        Log.d(TAG, "[onCreate]")
        super.onCreate()
        // requires android.permission.FOREGROUND_SERVICE
        ClientManager.getClient().addAudioPlayerListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[onStartCommand]")
        when (intent?.action) {
            ACTION_START_SERVICE -> onActionStartService(intent)
            ACTION_PLAY -> onActionPlay()
            ACTION_PREV -> onActionPrev()
            ACTION_NEXT -> onActionNext()
            ACTION_STOP -> onActionStop()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun onActionStartService(intent: Intent) {
        audioItemContext = with(intent) {
            AudioPlayerAgentInterface.Context(
                getStringExtra(EXTRA_AUDIO_ITEM_ID),
                getStringExtra(EXTRA_AUDIO_ITEM_TEMPLATE),
                getLongExtra(EXTRA_AUDIO_ITEM_OFFSET, -1L)
            )
        }

        startForeground(SERVICE_ID, getNotification(createDefaultRemoteViews()))
    }

    private fun onActionStop() {
        ClientManager.getClient().getPlaybackRouter().buttonPressed(PlaybackButton.STOP)
        stopServiceSelf()
    }

    private fun onActionPlay() {
        if (playerActivity == AudioPlayerAgentInterface.State.PLAYING) {
            ClientManager.getClient().getPlaybackRouter().buttonPressed(PlaybackButton.PAUSE)
        } else if (playerActivity == AudioPlayerAgentInterface.State.PAUSED) {
            ClientManager.getClient().getPlaybackRouter().buttonPressed(PlaybackButton.PLAY)
        }
    }

    private fun onActionPrev() {
        ClientManager.getClient().getPlaybackRouter().buttonPressed(PlaybackButton.PREVIOUS)
    }

    private fun onActionNext() {
        ClientManager.getClient().getPlaybackRouter().buttonPressed(PlaybackButton.NEXT)
    }

    override fun onDestroy() {
        ClientManager.getClient().removeAudioPlayerListener(this)
        super.onDestroy()
        Log.d(TAG, "[onDestroy]")
    }

    private fun getNotification(remoteViews: RemoteViews): Notification {
        Log.d(TAG, "[getNotification]")

        val builder: NotificationCompat.Builder = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            NotificationCompat.Builder(this)
        } else {
            NotificationCompat.Builder(this, SampleApplication.NOTIFICATION_CHANNEL_MUSIC_PLAYER)
        }.apply {
            setSmallIcon(R.drawable.logo_noti)
            setContent(remoteViews)
        }

        return builder.build()
    }

    private fun stopServiceSelf() {
        if (isDestroying) {
            Log.w(TAG, "[stopServiceSelf] already called")
            return
        }

        Log.d(TAG, "[stopServiceSelf]")
        isDestroying = true
        stopForeground(true)
        stopSelf()
    }

    override fun onStateChanged(activity: AudioPlayerAgentInterface.State, context: AudioPlayerAgentInterface.Context) {
        playerActivity = activity
        handler.post {
            Log.d(TAG, "[onStateChanged] activity")
            handler.removeCallbacks(stopServiceRunnable)
            invalidateNotification(createDefaultRemoteViews())
        }
    }

    private fun invalidateNotification(remoteViews: RemoteViews) {
        if (isDestroying) {
            Log.d(TAG, "[invalidateNotification] skip (service is destroying)")
            return
        }

        Log.d(TAG, "[invalidateNotification]")
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
            SERVICE_ID,
            getNotification(remoteViews)
        )
    }

    private fun createDefaultRemoteViews(): RemoteViews =
        RemoteViews(packageName, R.layout.remote_views_music_player).apply {
            setOnClickPendingIntent(
                R.id.iv_btn_noti_skip_previous,
                getPendingIntent(this@MusicPlayerService, ACTION_PREV)
            )
            setOnClickPendingIntent(R.id.iv_btn_noti_play, getPendingIntent(this@MusicPlayerService, ACTION_PLAY))
            setOnClickPendingIntent(R.id.iv_btn_noti_skip_next, getPendingIntent(this@MusicPlayerService, ACTION_NEXT))
            setOnClickPendingIntent(R.id.iv_btn_noti_close, getPendingIntent(this@MusicPlayerService, ACTION_STOP))

            when (playerActivity) {
                AudioPlayerAgentInterface.State.IDLE,
                AudioPlayerAgentInterface.State.FINISHED,
                AudioPlayerAgentInterface.State.STOPPED -> {
                    handler.postDelayed(stopServiceRunnable, 10000L)
                }
                AudioPlayerAgentInterface.State.PAUSED -> {
                    setImageViewResource(R.id.iv_btn_noti_play, R.drawable.ic_btn_noti_play)
                }

                AudioPlayerAgentInterface.State.PLAYING -> {
                    setImageViewResource(R.id.iv_btn_noti_play, R.drawable.ic_btn_pause)
                }
            }

            audioItemContext?.audioItemTemplate?.let {
                try {
                    with(JSONObject(it)) {
                        optJSONObject("title")?.let { title ->
                            setTextViewText(R.id.tv_title, title.optString("text", ""))
                            title.optString("logo")?.let {
                                val notificationTarget = NotificationTarget(applicationContext, R.id.iv_logo, this@apply, getNotification(this@apply), SERVICE_ID)
                                Glide.with(applicationContext).asBitmap().load(it).into(notificationTarget)
                            }
                        }

                        optJSONObject("content")?.let {content ->
                            setTextViewText(R.id.tv_content_title, content.optString("title", ""))

                            val subtitle1 = content.optString("subtitle1")
                            if(subtitle1 != null) {
                                setTextViewText(R.id.tv_content_subtitle, subtitle1)

                                setBoolean(R.id.tv_content_title, "setSingleLine", true)
                                setInt(R.id.tv_content_title, "setMaxLines", 1)
                                setViewVisibility(R.id.tv_content_subtitle, View.VISIBLE)
                            } else {
                                setBoolean(R.id.tv_content_title, "setSingleLine", false)
                                setInt(R.id.tv_content_title, "setMaxLines", 2)
                                setViewVisibility(R.id.tv_content_subtitle, View.GONE)
                            }

                            content.optString("imageUrl")?.let {
                                val notificationTarget = NotificationTarget(applicationContext, R.id.iv_content_image, this@apply, getNotification(this@apply), SERVICE_ID)
                                Glide.with(applicationContext).asBitmap().load(it).into(notificationTarget)
                            }
                        }
                    }
                } catch (ignore: Exception) {
                }
            }
        }

    private fun getPendingIntent(context: Context, action: String): PendingIntent {
        return PendingIntent.getService(
            context,
            0,
            Intent(context, MusicPlayerService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}