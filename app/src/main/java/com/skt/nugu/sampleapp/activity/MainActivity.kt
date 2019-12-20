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
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import com.skt.nugu.sdk.platform.android.login.auth.AuthStateListener
import com.skt.nugu.sdk.core.interfaces.capability.audioplayer.AudioPlayerAgentInterface
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.platform.android.ux.widget.NuguFloatingActionButton
import com.skt.nugu.sdk.platform.android.ux.widget.Snackbar
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregator
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregatorInterface
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import com.skt.nugu.sampleapp.BuildConfig
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.service.MusicPlayerService
import com.skt.nugu.sampleapp.template.FragmentTemplateRenderer
import com.skt.nugu.sampleapp.utils.*
import com.skt.nugu.sampleapp.widget.BottomSheetController

class MainActivity : AppCompatActivity(), SpeechRecognizerAggregatorInterface.OnStateChangeListener,
    NavigationView.OnNavigationItemSelectedListener
    , AuthStateListener
    , ConnectionStatusListener
    , AudioPlayerAgentInterface.Listener {
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

    private val btnStartListening: NuguFloatingActionButton by lazy {
        findViewById<NuguFloatingActionButton>(R.id.fab)
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
    
    private lateinit var bottomSheetController: BottomSheetController
    private var bottomSheetVisibility = View.INVISIBLE

    private val speechRecognizerAggregator: SpeechRecognizerAggregator by lazy {
        ClientManager.speechRecognizerAggregator
    }

    private val templateRenderer = FragmentTemplateRenderer(supportFragmentManager, R.id.container)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initBtnListeners()
        SoundPoolCompat.load(this)
        // add observer for connection
        ClientManager.getClient().addConnectionListener(this)
        // add observer for audioplayer
        ClientManager.getClient().addAudioPlayerListener(this)
        ClientManager.getClient().setDisplayRenderer(templateRenderer)

        bottomSheetController = BottomSheetController(this, object : BottomSheetController.OnBottomSheetCallback {
            override fun onExpandStarted() {
                bottomSheetVisibility = View.VISIBLE
                btnStartListening.visibility = View.INVISIBLE
            }

            override fun onHiddenFinished() {
                bottomSheetVisibility = View.INVISIBLE
                btnStartListening.visibility = View.VISIBLE
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
            speechRecognizerAggregator.changeKeywordResource(ClientManager.ariaResource)
        } else {
            speechRecognizerAggregator.changeKeywordResource(ClientManager.tinkerbellResource)
        }
        speechRecognizerAggregator.addListener(this)
        NuguOAuth.getClient().addAuthStateListener(this)

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
        NuguOAuth.getClient().removeAuthStateListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        bottomSheetController.apply {
            speechRecognizerAggregator.removeListener(this)
            ClientManager.getClient().removeDialogUXStateListener(this)
            ClientManager.getClient().removeASRResultListener(this)
        }

        SoundPoolCompat.release()
        MusicPlayerService.stopService(this)

        ClientManager.getClient().setDisplayRenderer(null)
        // remove observer for connection
        ClientManager.getClient().removeConnectionListener(this)
        // shutdown a server
        ClientManager.getClient().shutdown()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if(bottomSheetVisibility == View.VISIBLE) {
            ClientManager.speechRecognizerAggregator.stopListening()
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
                            speechRecognizerAggregator.startListening(
                                null,
                                null
                            )
                        }

                        override fun onDenied() {
                            Log.d(TAG, "[requestPermissions::onDenied]")
                        }

                        override fun onCanceled() {
                            Log.d(TAG, "[requestPermissions::onCanceled]")
                        }
                    })
            } else {
                speechRecognizerAggregator.startListening(
                    null,
                    null
                )
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
        runOnUiThread {
            if (speechRecognizerAggregatorState != state) {
                when (state) {
                    SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH -> {
                        if (VolumeUtils.isMute(this)) {
                            VolumeUtils.unMute(this)
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

    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_setting -> {
                SettingsActivity.invokeActivity(this)
            }
        }
        return true
    }

    override fun onAuthStateChanged(newState: AuthStateListener.State): Boolean {
        if (newState == AuthStateListener.State.UNRECOVERABLE_ERROR) {
            runOnUiThread {
                Snackbar.with(findViewById(R.id.drawer_layout))
                    .message(R.string.device_not_connected)
                    .duration(Snackbar.LENGTH_LONG)
                    .callback(object : Snackbar.Callback() {
                        override fun onDismissed() {
                            LoadingActivity.invokeActivity(this@MainActivity)
                            finishAffinity()
                        }
                    }).show()
            }
        }
        return true
    }

    private fun updateView() {
        // Set the enabled state of this [btnStartListening]
        runOnUiThread {
            btnStartListening.isEnabled = isConnected()
            //
            if (PreferenceHelper.enableTrigger(this)) {
                btnStartListening.resumeAnimation()
            } else {
                btnStartListening.stopAnimation()
            }

            if (PreferenceHelper.enableNugu(this) && bottomSheetVisibility == View.INVISIBLE) {
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
    }

    override fun onStateChanged(activity: AudioPlayerAgentInterface.State, context: AudioPlayerAgentInterface.Context) {
        runOnUiThread {
            if (activity == AudioPlayerAgentInterface.State.PLAYING) {
                MusicPlayerService.startService(this, context)
            }
        }
    }
}
