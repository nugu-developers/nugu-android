package com.skt.nugu.sampleapp.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.activity.main.MainActivity
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.service.floating.FloatingHeadWindow
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SampleAppService : Service() {
    companion object {
        private const val TAG = "SampleAppService"

        private const val ACTION_SERVICE_START = "ACTION_START"
        private const val ACTION_SERVICE_STOP = "ACTION_STOP"
        private const val ACTION_SHOW_FLOATING = "ACTION_SHOW_FLOATING"
        private const val ACTION_HIDE_FLOATING = "ACTION_HIDE_FLOATING"

        fun start(appContext: Context) {
            ContextCompat.startForegroundService(appContext,
                Intent(appContext, SampleAppService::class.java).also { it.action = ACTION_SERVICE_START })
        }

        fun stop(appContext: Context) {
            ContextCompat.startForegroundService(appContext,
                Intent(appContext, SampleAppService::class.java).also { it.action = ACTION_SERVICE_STOP })
        }

        fun showFloating(appContext: Context) {
            ContextCompat.startForegroundService(appContext,
                Intent(appContext, SampleAppService::class.java).also { it.action = ACTION_SHOW_FLOATING })
        }

        fun hideFloating(appContext: Context) {
            ContextCompat.startForegroundService(appContext,
                Intent(appContext, SampleAppService::class.java).also { it.action = ACTION_HIDE_FLOATING })
        }
    }

    private val appContext: Context by inject()
    lateinit var floatingHeadWindow: FloatingHeadWindow
    private var medianNotiCancleJob: Job? = null
    private var notifyingTemplateId: String? = null

    private val notiRenderer = object : TemplateRenderer.ExternalViewRenderer {
        override fun getVisibleList(): List<TemplateRenderer.ExternalViewRenderer.ViewInfo>? {
            notifyingTemplateId.let { id ->
                return if (id == null) null
                else listOf(TemplateRenderer.ExternalViewRenderer.ViewInfo(id))
            }
        }
    }

    private val audioStateListener = object : AudioPlayerAgentInterface.Listener {
        override fun onStateChanged(activity: AudioPlayerAgentInterface.State, context: AudioPlayerAgentInterface.Context) {
            if (activity == AudioPlayerAgentInterface.State.PLAYING) {
                MusicPlayerService.startService(appContext, context)
                medianNotiCancleJob?.cancel()

                notifyingTemplateId = context.templateId
            } else if (activity == AudioPlayerAgentInterface.State.STOPPED) {
                medianNotiCancleJob = GlobalScope.launch {
                    delay(1000)
                    MusicPlayerService.stopService(appContext)
                }
                notifyingTemplateId = null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground()

        initFloatingButton()
        ClientManager.getClient().addAudioPlayerListener(audioStateListener)
        MainActivity.templateRenderer.externalViewRenderer = notiRenderer
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingHeadWindow.hide()
        ClientManager.getClient().removeAudioPlayerListener(audioStateListener)
        stopForeGround()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_SHOW_FLOATING -> floatingHeadWindow.show()
            ACTION_HIDE_FLOATING -> floatingHeadWindow.hide()
            ACTION_SERVICE_STOP -> stopForeGround()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initFloatingButton() {
        if (!::floatingHeadWindow.isInitialized) {
            floatingHeadWindow = FloatingHeadWindow(applicationContext).apply {
                create()
                createLayoutParams()
            }
        }
    }

    private fun startForeground() {
        startForeground(123, SampleNoti(appContext).builder.build())
    }

    private fun stopForeGround() {
        stopForeground(true)
        stopSelf()
    }

    class SampleNoti(val context: Context) {
        val builder: NotificationCompat.Builder by lazy {
            NotificationCompat.Builder(context, SampleNotificationChannel.SAMPLE.channelId)
                .setSmallIcon(R.drawable.nugu_logo_72)  // the status icon
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setSound(null)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentText("nugu is running")
                .setStyle(NotificationCompat.BigTextStyle().bigText(""))
        }
    }
}