package com.skt.nugu.sampleapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import com.skt.nugu.sampleapp.activity.MainActivity
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

        const val ACTION_SERVICE_START = "ACTION_START"
        const val ACTION_SERVICE_STOP = "ACTION_STOP"

        fun start(appContext: Context, serviceConnection: ServiceConnection) {
            appContext.bindService(Intent(appContext, SampleAppService::class.java), serviceConnection, BIND_AUTO_CREATE)
//            appContext.startService(Intent(appContext, SampleAppService::class.java).also { it.action = ACTION_SERVICE_START })
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): SampleAppService {
            return this@SampleAppService
        }
    }

    private val appContext: Context by inject()
    lateinit var floatingHeadWindow: FloatingHeadWindow
    private val mBinder = LocalBinder()
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

    fun init() {
        if (!::floatingHeadWindow.isInitialized) {
            floatingHeadWindow = FloatingHeadWindow(applicationContext).apply {
                create()
                createLayoutParams()
            }
        }
    }

    fun show(){
        floatingHeadWindow.show()
    }

    fun hide(){
        floatingHeadWindow.hide()
    }

    override fun onCreate() {
        super.onCreate()
        init()
        ClientManager.getClient().addAudioPlayerListener(audioStateListener)
        MainActivity.templateRenderer.externalViewRenderer = notiRenderer
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingHeadWindow.hide()
        ClientManager.getClient().removeAudioPlayerListener(audioStateListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = mBinder
}