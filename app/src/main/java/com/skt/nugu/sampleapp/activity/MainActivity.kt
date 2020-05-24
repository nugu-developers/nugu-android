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
package com.skt.nugu.sampleapp.activity

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.skt.nugu.sdk.platform.android.login.auth.AuthStateListener
import com.skt.nugu.sdk.agent.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.platform.android.ux.widget.NuguSnackbar
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregator
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregatorInterface
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import com.skt.nugu.sampleapp.BuildConfig
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.service.MusicPlayerService
import com.skt.nugu.sampleapp.template.FragmentTemplateRenderer
import com.skt.nugu.sampleapp.utils.*
import com.skt.nugu.sampleapp.widget.ChromeWindowController
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import com.skt.nugu.sdk.platform.android.ux.widget.NuguButton
import com.skt.nugu.sdk.platform.android.ux.widget.NuguToast

class MainActivity : AppCompatActivity(), SpeechRecognizerAggregatorInterface.OnStateChangeListener,
    NavigationView.OnNavigationItemSelectedListener
    , ConnectionStatusListener
    , AudioPlayerAgentInterface.Listener
    , SystemAgentInterfaceListener {
    companion object {
        private const val TAG = "MainActivity"
        private val permissions = arrayOf(
            android.Manifest.permission.RECORD_AUDIO
        )
        private const val requestCode = 100

        fun invokeActivity(context: Context) {
            context.startActivity(Intent(context, MainActivity::class.java))
        }
    }

    @Volatile
    private var connectionStatus: ConnectionStatusListener.Status = ConnectionStatusListener.Status.DISCONNECTED

    private val btnStartListening: NuguButton by lazy {
        findViewById<NuguButton>(R.id.fab)
    }
    private val drawerLayout: DrawerLayout by lazy {
        findViewById<DrawerLayout>(R.id.drawer_layout)
    }
    private val navView: NavigationView by lazy {
        findViewById<NavigationView>(R.id.nav_view)
    }

    private val version: TextView by lazy {
        findViewById<TextView>(R.id.tv_version)
    }

    private val toolBar: android.support.v7.widget.Toolbar by lazy {
        findViewById<android.support.v7.widget.Toolbar>(R.id.toolbar)
    }
    private val onRequestPermissionResultHandler: OnRequestPermissionResultHandler by lazy {
        OnRequestPermissionResultHandler(this)
    }
    
    private lateinit var chromeWindowController: ChromeWindowController

    private val speechRecognizerAggregator: SpeechRecognizerAggregator by lazy {
        ClientManager.speechRecognizerAggregator
    }
    private val containerIds = mutableMapOf(
        "Display" to R.id.container,
        "AudioPlayer" to R.id.sliding_container
    )

    private val templateRenderer = FragmentTemplateRenderer(supportFragmentManager,containerIds)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initBtnListeners()
        SoundPoolCompat.load(this)
        // add observer for connection
        ClientManager.getClient().addConnectionListener(this)
        // add observer for audioplayer
        ClientManager.getClient().addAudioPlayerListener(this)
        // Set a renderer for display agent.
        ClientManager.getClient().setDisplayRenderer(templateRenderer)
        // add listener for system agent.
        ClientManager.getClient().addSystemAgentListener(this)

        chromeWindowController = ChromeWindowController(this, object : ChromeWindowController.OnChromeWindowCallback {
            override fun onExpandStarted() {
                if(btnStartListening.isFab()) {
                    btnStartListening.visibility = View.INVISIBLE
                } else /* Type is button */{
                    btnStartListening.isActivated = true
                }
            }

            override fun onHiddenFinished() {
                if(btnStartListening.isFab()) {
                    btnStartListening.visibility = View.VISIBLE
                }  else /* Type is button */{
                    btnStartListening.isActivated = false
                }
                speechRecognizerAggregator.stopListening()
            }
        }).apply {
            speechRecognizerAggregator.addListener(this)
            ClientManager.getClient().addDialogUXStateListener(this)
            ClientManager.getClient().addASRResultListener(this)
        }

        version.text = "v${BuildConfig.VERSION_NAME}"
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "[onResume]")
        if (PreferenceHelper.triggerId(this) == 0) {
            ClientManager.keywordDetector?.keywordResource = ClientManager.ariaResource
        } else {
            ClientManager.keywordDetector?.keywordResource = ClientManager.tinkerbellResource
        }
        speechRecognizerAggregator.addListener(this)

        // Check Permission
        if (!PermissionUtils.checkPermissions(this, permissions)) {
            onRequestPermissionResultHandler.requestPermissions(
                this,
                permissions,
                requestCode,
                object : OnRequestPermissionResultHandler.OnPermissionListener {
                    override fun onGranted() {
                        tryStartListeningWithTrigger()
                    }

                    override fun onDenied() {
                    }

                    override fun onCanceled() {
                    }
                })
        } else {
            tryStartListeningWithTrigger()
        }
        // connect to server
        ClientManager.getClient().connect()
        // update view
        updateView()
    }

    private fun tryStartListeningWithTrigger() {
        if (PreferenceHelper.enableNugu(this) && PreferenceHelper.enableTrigger(this) && !speechRecognizerAggregator.isActive()) {
            speechRecognizerAggregator.startListeningWithTrigger()
        }
    }

    override fun onPause() {
        Log.d(TAG, "[onPause]")
        speechRecognizerAggregator.removeListener(this)
        speechRecognizerAggregator.stop()
        super.onPause()
    }

    override fun onDestroy() {
        chromeWindowController.apply {
            speechRecognizerAggregator.removeListener(this)
            ClientManager.getClient().removeDialogUXStateListener(this)
            ClientManager.getClient().removeASRResultListener(this)
        }

        SoundPoolCompat.release()
        MusicPlayerService.stopService(this)

        // clear a renderer for display agent.
        ClientManager.getClient().setDisplayRenderer(null)
        // remove observer for connection
        ClientManager.getClient().removeConnectionListener(this)
        // remove listener for systemagent
        ClientManager.getClient().removeSystemAgentListener(this)
        // shutdown a server
        ClientManager.getClient().shutdown()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if(chromeWindowController.isShown()) {
            chromeWindowController.dismiss()
            return
        }

        super.onBackPressed()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        // Forward results to PermissionUtils
        onRequestPermissionResultHandler.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun initBtnListeners() {
        btnStartListening.setOnClickListener {
            if (!PermissionUtils.checkPermissions(this, permissions)) {
                onRequestPermissionResultHandler.requestPermissions(
                    this,
                    permissions,
                    requestCode,
                    object : OnRequestPermissionResultHandler.OnPermissionListener {
                        override fun onGranted() {
                            speechRecognizerAggregator.startListening()
                        }

                        override fun onDenied() {
                            Log.d(TAG, "[requestPermissions::onDenied]")
                        }

                        override fun onCanceled() {
                            Log.d(TAG, "[requestPermissions::onCanceled]")
                        }
                    })
            } else {
                speechRecognizerAggregator.startListening()
            }
        }

        ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolBar,
            0,
            0
        ).apply {
            drawerLayout.addDrawerListener(this)
            syncState()
        }

        navView.setNavigationItemSelectedListener(this)
    }

    var speechRecognizerAggregatorState: SpeechRecognizerAggregatorInterface.State =
        SpeechRecognizerAggregatorInterface.State.WAITING

    override fun onStateChanged(state: SpeechRecognizerAggregatorInterface.State) {
        Log.d(TAG, "[onStateChanged::$state]")
        if (speechRecognizerAggregatorState != state) {
            when (state) {
                SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH -> {
                    if (VolumeUtils.isMute(this)) {
                        runOnUiThread {
                            NuguToast.with(findViewById(R.id.drawer_layout))
                                .message(R.string.volume_mute)
                                .yOffset(findViewById<FrameLayout>(R.id.fl_bottom_sheet).height)
                                .duration(NuguSnackbar.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
                else -> {
                }
            }
        }

        if(state != SpeechRecognizerAggregatorInterface.State.ERROR) {
            tryStartListeningWithTrigger()
        }
        speechRecognizerAggregatorState = state
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_setting -> {
                SettingsActivity.invokeActivity(this)
            }
        }
        return true
    }

    private fun updateView() {
        // Set the enabled state of this [btnStartListening]
        runOnUiThread {
            if(!PreferenceHelper.enableNugu(this)) {
                btnStartListening.visibility = View.INVISIBLE
                return@runOnUiThread
            }

            btnStartListening.isEnabled = isConnected()
            if (!chromeWindowController.isShown()) {
                btnStartListening.visibility = View.VISIBLE
            } else {
                btnStartListening.visibility = View.INVISIBLE
            }
        }
    }

    private fun isConnected(): Boolean {
        return connectionStatus == ConnectionStatusListener.Status.CONNECTED
    }

    override fun onConnectionStatusChanged(status: ConnectionStatusListener.Status, reason: ConnectionStatusListener.ChangedReason) {
        if (connectionStatus != status) {
            connectionStatus = status
            updateView()
        }

        if(status == ConnectionStatusListener.Status.CONNECTING) {
            when(reason) {
                ConnectionStatusListener.ChangedReason.UNRECOVERABLE_ERROR,
                ConnectionStatusListener.ChangedReason.DNS_TIMEDOUT,
                ConnectionStatusListener.ChangedReason.CONNECTION_ERROR,
                ConnectionStatusListener.ChangedReason.CONNECTION_TIMEDOUT,
                ConnectionStatusListener.ChangedReason.REQUEST_TIMEDOUT,
                ConnectionStatusListener.ChangedReason.PING_TIMEDOUT,
                ConnectionStatusListener.ChangedReason.FAILURE_PROTOCOL_ERROR,
                ConnectionStatusListener.ChangedReason.INTERNAL_ERROR,
                ConnectionStatusListener.ChangedReason.SERVER_INTERNAL_ERROR,
                ConnectionStatusListener.ChangedReason.SERVER_SIDE_DISCONNECT,
                ConnectionStatusListener.ChangedReason.SERVER_ENDPOINT_CHANGED -> {
                    /** checking connection status for debugging. **/
                    NuguSnackbar.with(findViewById(R.id.drawer_layout))
                        .message(R.string.reconnecting)
                        .duration(NuguSnackbar.LENGTH_SHORT)
                        .show()
                }
                else -> { /* nothing to do */ }
            }
        } else if(status == ConnectionStatusListener.Status.DISCONNECTED) {
            when (reason) {
                ConnectionStatusListener.ChangedReason.NONE,
                ConnectionStatusListener.ChangedReason.SUCCESS,
                ConnectionStatusListener.ChangedReason.CLIENT_REQUEST,
                ConnectionStatusListener.ChangedReason.SERVER_ENDPOINT_CHANGED -> { /** no error **/ }
                ConnectionStatusListener.ChangedReason.UNRECOVERABLE_ERROR,
                ConnectionStatusListener.ChangedReason.DNS_TIMEDOUT,
                ConnectionStatusListener.ChangedReason.CONNECTION_ERROR,
                ConnectionStatusListener.ChangedReason.CONNECTION_TIMEDOUT,
                ConnectionStatusListener.ChangedReason.REQUEST_TIMEDOUT,
                ConnectionStatusListener.ChangedReason.PING_TIMEDOUT,
                ConnectionStatusListener.ChangedReason.FAILURE_PROTOCOL_ERROR,
                ConnectionStatusListener.ChangedReason.INTERNAL_ERROR,
                ConnectionStatusListener.ChangedReason.SERVER_INTERNAL_ERROR,
                ConnectionStatusListener.ChangedReason.SERVER_SIDE_DISCONNECT -> {
                    /** If you want to reconnect to the server, run the code below.
                        But it can be recursive, so you need to manage the count of attempts.
                        if(attempts++ < maxAttempts) {
                            ClientManager.getClient().connect()
                        }
                     **/
                    NuguSnackbar.with(findViewById(R.id.drawer_layout))
                        .message(R.string.connection_failed)
                        .duration(NuguSnackbar.LENGTH_LONG)
                        .show()

                    val cm = this.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
                    if(activeNetwork?.isConnected == true) {
                        SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_SERVER_ERROR_TRY_AGAIN)
                    } else {
                        SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_NETWORK_ERROR)
                    }
                }
                ConnectionStatusListener.ChangedReason.INVALID_AUTH -> {
                    /** Authentication failed Please refresh your access_token. **/
                    NuguOAuth.getClient().login(object : AuthStateListener {
                        override fun onAuthStateChanged(newState: AuthStateListener.State): Boolean {
                            when(newState) {
                                AuthStateListener.State.REFRESHED -> {
                                    Log.d(TAG, "Connect to the server")
                                    ClientManager.getClient().connect()
                                    return false
                                }
                                AuthStateListener.State.UNRECOVERABLE_ERROR -> {
                                    Log.d(TAG, "Authentication failed")
                                    NuguSnackbar.with(findViewById(R.id.drawer_layout))
                                        .message(R.string.authentication_failed)
                                        .duration(NuguSnackbar.LENGTH_LONG)
                                        .show()
                                    SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_UNAUTHORIZED_ERROR)
                                    return false
                                }
                                AuthStateListener.State.UNINITIALIZED,
                                AuthStateListener.State.EXPIRED -> {
                                    Log.d(TAG, "Authentication in progress")
                                }
                            }

                            return true
                        }
                    })
                }
            }
        }
    }

    override fun onStateChanged(activity: AudioPlayerAgentInterface.State, context: AudioPlayerAgentInterface.Context) {
        runOnUiThread {
            if (activity == AudioPlayerAgentInterface.State.PLAYING) {
                MusicPlayerService.startService(this, context)
            }
        }
    }

    override fun onRevoke(reason: SystemAgentInterface.RevokeReason) {
        Log.d(TAG, "[onRevoke] The device has been revoked ($reason)")
        ClientManager.getClient().disconnect()
        PreferenceHelper.credentials(this@MainActivity, "")
    }

    override fun onException(code: SystemAgentInterface.ExceptionCode, description: String?) {
        when(code) {
            SystemAgentInterface.ExceptionCode.PLAY_ROUTER_PROCESSING_EXCEPTION -> {
                SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_PLAY_ROUTER_ERROR)
            }
            SystemAgentInterface.ExceptionCode.TTS_SPEAKING_EXCEPTION -> {
                SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_TTS_ERROR)
            }
            SystemAgentInterface.ExceptionCode.UNAUTHORIZED_REQUEST_EXCEPTION -> {
                /** Nothing to do because handle on [onConnectionStatusChanged] **/
            }
        }
    }
}
