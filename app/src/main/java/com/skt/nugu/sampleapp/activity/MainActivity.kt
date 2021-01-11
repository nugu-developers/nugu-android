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

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.provider.Settings
import android.support.design.widget.NavigationView
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregator
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregatorInterface
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import com.skt.nugu.sampleapp.BuildConfig
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.client.ExponentialBackOff
import com.skt.nugu.sampleapp.client.TokenRefresher
import com.skt.nugu.sampleapp.service.SampleAppService
import com.skt.nugu.sampleapp.utils.*
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.login.auth.Credentials
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthError
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import com.skt.nugu.sdk.platform.android.ux.widget.*

class MainActivity : AppCompatActivity(), SpeechRecognizerAggregatorInterface.OnStateChangeListener,
    NavigationView.OnNavigationItemSelectedListener, ConnectionStatusListener, SystemAgentInterfaceListener,
    TokenRefresher.Listener {
    companion object {
        private const val TAG = "MainActivity"
        private val permissions = arrayOf(
            android.Manifest.permission.RECORD_AUDIO
        )
        private const val requestCode = 100

        fun invokeActivity(context: Context) {
            context.startActivity(Intent(context, MainActivity::class.java))
        }

        val templateRenderer: TemplateRenderer by lazy {
            TemplateRenderer(object : TemplateRenderer.NuguClientProvider {
                override fun getNuguClient(): NuguAndroidClient = ClientManager.getClient()
            }, ConfigurationStore.configuration.deviceTypeCode, null, R.id.template_container)
        }
    }

    enum class NotificationType {
        TOAST, VOICE
    }

    private var notificationType = NotificationType.TOAST

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
    private val chromeWindow: ChromeWindow by lazy {
        ChromeWindow(this, findViewById<CoordinatorLayout>(R.id.coordinator))
    }

    private val onRequestPermissionResultHandler: OnRequestPermissionResultHandler by lazy {
        OnRequestPermissionResultHandler(this)
    }

    private val speechRecognizerAggregator: SpeechRecognizerAggregator by lazy {
        ClientManager.speechRecognizerAggregator
    }

    private val tokenRefresher = TokenRefresher(NuguOAuth.getClient())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initBtnListeners()
        SoundPoolCompat.load(this)
        // add observer for connection
        ClientManager.getClient().addConnectionListener(this)
        // Set a renderer for display agent.
        ClientManager.getClient().setDisplayRenderer(templateRenderer.also {
            it.setFragmentManager(supportFragmentManager)

            ConfigurationStore.templateServerUri { url, error ->
                error?.apply {
                    Log.e(TAG, "[onCreate] error=$this")
                    return@templateServerUri
                }
                it.setServerUrl(url)
            }
        })
        // add listener for system agent.
        ClientManager.getClient().addSystemAgentListener(this)

        chromeWindow.setOnChromeWindowCallback(object : ChromeWindow.OnChromeWindowCallback {
            override fun onExpandStarted() {
                if (btnStartListening.isFab()) {
                    btnStartListening.visibility = View.INVISIBLE
                } else /* Type is button */ {
                    btnStartListening.isActivated = true
                }
            }

            override fun onHiddenFinished() {
                if (btnStartListening.isFab()) {
                    btnStartListening.visibility = View.VISIBLE
                } else /* Type is button */ {
                    btnStartListening.isActivated = false
                }
                speechRecognizerAggregator.stopListening()
            }

            override fun onChipsClicked(item: NuguChipsView.Item) {
                ClientManager.getClient().requestTextInput(item.text)
            }
        })

        chromeWindow.apply {
            speechRecognizerAggregator.addListener(this)
            ClientManager.getClient().addDialogUXStateListener(this)
            ClientManager.getClient().addASRResultListener(this)
            ClientManager.getClient().ttsAgent?.addListener(this)
        }

        version.text = "v${BuildConfig.VERSION_NAME}"

        tokenRefresher.setListener(this)
        tokenRefresher.start()

        checkPermissionForOverlay()
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
        chromeWindow.apply {
            speechRecognizerAggregator.removeListener(this)
            ClientManager.getClient().removeDialogUXStateListener(this)
            ClientManager.getClient().removeASRResultListener(this)
            ClientManager.getClient().ttsAgent?.removeListener(this)
        }

        SoundPoolCompat.release()

        // clear a renderer for display agent.
        ClientManager.getClient().setDisplayRenderer(null)
        templateRenderer.setFragmentManager(null)

        // remove observer for connection
        ClientManager.getClient().removeConnectionListener(this)
        // remove listener for systemagent
        ClientManager.getClient().removeSystemAgentListener(this)
        // shutdown a server
//        ClientManager.getClient().shutdown()

        tokenRefresher.stop()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (chromeWindow.isShown()) {
            chromeWindow.dismiss()
            return
        }

        if (templateRenderer.clearAll()) return

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
                            NuguToast.with(this)
                                .message(R.string.volume_mute)
                                .yOffset(findViewById<FrameLayout>(R.id.fl_bottom_sheet).height)
                                .duration(NuguToast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
                else -> {
                }
            }
        }

        if (state != SpeechRecognizerAggregatorInterface.State.ERROR) {
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
            if (!PreferenceHelper.enableNugu(this)) {
                btnStartListening.visibility = View.INVISIBLE
                return@runOnUiThread
            }

            btnStartListening.isEnabled = isConnected()
            if (!chromeWindow.isShown()) {
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

        if (status == ConnectionStatusListener.Status.CONNECTING) {
            when (reason) {
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
                ConnectionStatusListener.ChangedReason.SERVER_ENDPOINT_CHANGED
                -> {
                    /** checking connection status for debugging. **/
                    Log.d(TAG, "reconnecting(reason=$reason)")
                }
                else -> { /* nothing to do */
                }
            }
        } else if (status == ConnectionStatusListener.Status.DISCONNECTED) {
            when (reason) {
                ConnectionStatusListener.ChangedReason.NONE,
                ConnectionStatusListener.ChangedReason.SUCCESS,
                ConnectionStatusListener.ChangedReason.CLIENT_REQUEST,
                ConnectionStatusListener.ChangedReason.SERVER_ENDPOINT_CHANGED
                -> {
                    /** no error **/
                }
                ConnectionStatusListener.ChangedReason.UNRECOVERABLE_ERROR,
                ConnectionStatusListener.ChangedReason.DNS_TIMEDOUT,
                ConnectionStatusListener.ChangedReason.CONNECTION_ERROR,
                ConnectionStatusListener.ChangedReason.CONNECTION_TIMEDOUT,
                ConnectionStatusListener.ChangedReason.REQUEST_TIMEDOUT,
                ConnectionStatusListener.ChangedReason.PING_TIMEDOUT,
                ConnectionStatusListener.ChangedReason.FAILURE_PROTOCOL_ERROR,
                ConnectionStatusListener.ChangedReason.INTERNAL_ERROR,
                ConnectionStatusListener.ChangedReason.SERVER_INTERNAL_ERROR,
                ConnectionStatusListener.ChangedReason.SERVER_SIDE_DISCONNECT
                -> {
                    /**
                     * only server-initiative-directive
                     * If you want to reconnect to the server, run the code below.
                     * But it can be recursive, so you need to manage the count of attempts.
                     **/
                    if (NuguOAuth.getClient().isSidSupported()) {
                        ExponentialBackOff.awaitConnectedAndRetry(this, object : ExponentialBackOff.Callback {
                            override fun onRetry() {
                                ClientManager.getClient().disconnect()
                                ClientManager.getClient().connect()
                            }

                            override fun onError(reason: ExponentialBackOff.ErrorCode) {
                                if (isNetworkConnected()) {
                                    when (notificationType) {
                                        NotificationType.TOAST ->
                                            NuguSnackbar.with(findViewById(R.id.drawer_layout))
                                                .message(R.string.device_gw_error_002)
                                                .duration(NuguSnackbar.LENGTH_LONG)
                                                .show()
                                        NotificationType.VOICE ->
                                            SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_SERVER_ERROR_TRY_AGAIN)
                                    }
                                } else {
                                    when (notificationType) {
                                        NotificationType.TOAST ->
                                            NuguSnackbar.with(findViewById(R.id.drawer_layout))
                                                .message(R.string.device_gw_error_001)
                                                .duration(NuguSnackbar.LENGTH_LONG)
                                                .show()
                                        NotificationType.VOICE ->
                                            SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_NETWORK_ERROR)
                                    }
                                }
                            }
                        })
                    }

                }
                ConnectionStatusListener.ChangedReason.INVALID_AUTH -> {
                    /** Authentication failed Please refresh your access_token. **/
                    performRevoke()
                }
            }
        } else if (status == ConnectionStatusListener.Status.CONNECTED) {
            ExponentialBackOff.reset()
        }
    }

    override fun onRevoke(reason: SystemAgentInterface.RevokeReason) {
        Log.d(TAG, "[onRevoke] The device has been revoked ($reason)")
        performRevoke()
    }

    override fun onException(code: SystemAgentInterface.ExceptionCode, description: String?) {
        when (code) {
            SystemAgentInterface.ExceptionCode.PLAY_ROUTER_PROCESSING_EXCEPTION -> {
                when (notificationType) {
                    NotificationType.TOAST ->
                        NuguSnackbar.with(findViewById(R.id.drawer_layout))
                            .message(R.string.device_gw_error_006)
                            .duration(NuguSnackbar.LENGTH_LONG)
                            .show()
                    NotificationType.VOICE ->
                        SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_PLAY_ROUTER_ERROR)
                }
            }
            SystemAgentInterface.ExceptionCode.TTS_SPEAKING_EXCEPTION -> {
                when (notificationType) {
                    NotificationType.TOAST ->
                        NuguSnackbar.with(findViewById(R.id.drawer_layout))
                            .message(R.string.device_gw_error_006)
                            .duration(NuguSnackbar.LENGTH_LONG)
                            .show()
                    NotificationType.VOICE ->
                        SoundPoolCompat.play(SoundPoolCompat.LocalTTS.DEVICE_GATEWAY_TTS_ERROR)
                }
            }
            SystemAgentInterface.ExceptionCode.UNAUTHORIZED_REQUEST_EXCEPTION -> {
                /** Nothing to do because handle on [onConnectionStatusChanged] **/
            }
        }
    }

    /**
     * Please check if you can renew the refresh_token according to device condition
     * It is recommended to proceed to the idle state.
     * Return {@code true} if the token needs to be refresh_token, Otherwise, it is called 30 seconds again.
     */
    override fun onShouldRefreshToken(): Boolean {
        if (chromeWindow.isShown()) {
            return false
        }
        return true
    }

    override fun onCredentialsChanged(credentials: Credentials) {
        PreferenceHelper.credentials(this, credentials.toString())
        ClientManager.getClient().disconnect()
        ClientManager.getClient().connect()
    }

    /** See more details in [LoginActivity.handleOAuthError] **/
    override fun onRefreshTokenError(error: NuguOAuthError) {
        Log.e(TAG, "An unexpected error has occurred. " +
                "Please check the logs for details\n" +
                "$error")

        if (error.error == NuguOAuthError.NETWORK_ERROR) {
            NuguSnackbar.with(findViewById(R.id.baseLayout))
                .message(R.string.device_gw_error_006)
                .show()
            return
        } else {
            when (error.code) {
                NuguOAuthError.USER_ACCOUNT_CLOSED -> {
                    NuguSnackbar.with(findViewById(R.id.baseLayout))
                        .message(R.string.code_user_account_closed)
                        .show()
                }
                NuguOAuthError.USER_ACCOUNT_PAUSED -> {
                    NuguSnackbar.with(findViewById(R.id.baseLayout))
                        .message(R.string.code_user_account_paused)
                        .show()
                }
                NuguOAuthError.USER_DEVICE_DISCONNECTED -> {
                    NuguSnackbar.with(findViewById(R.id.baseLayout))
                        .message(R.string.code_user_device_disconnected)
                        .show()
                }
                NuguOAuthError.USER_DEVICE_UNEXPECTED -> {
                    NuguSnackbar.with(findViewById(R.id.baseLayout))
                        .message(R.string.code_user_device_unexpected)
                        .show()
                }
                else -> {
                    when (error.error) {
                        NuguOAuthError.UNAUTHORIZED -> {
                            NuguSnackbar.with(findViewById(R.id.baseLayout))
                                .message(R.string.error_unauthorized)
                                .show()
                        }
                        NuguOAuthError.UNAUTHORIZED_CLIENT -> {
                            NuguSnackbar.with(findViewById(R.id.baseLayout))
                                .message(R.string.error_unauthorized_client)
                                .show()
                        }
                        NuguOAuthError.INVALID_TOKEN -> {
                            NuguSnackbar.with(findViewById(R.id.baseLayout))
                                .message(R.string.error_invalid_token)
                                .show()
                        }
                        NuguOAuthError.INVALID_CLIENT -> {
                            if (error.description == NuguOAuthError.FINISHED) {
                                NuguSnackbar.with(findViewById(R.id.baseLayout))
                                    .message(R.string.service_finished)
                                    .show()
                            } else {
                                NuguSnackbar.with(findViewById(R.id.baseLayout))
                                    .message(R.string.error_invalid_client)
                                    .show()
                            }
                        }
                        NuguOAuthError.ACCESS_DENIED -> {
                            NuguSnackbar.with(findViewById(R.id.baseLayout))
                                .message(R.string.error_access_denied)
                                .show()
                        }
                        else -> {
                            // check detail
                            NuguSnackbar.with(findViewById(R.id.baseLayout))
                                .message(R.string.device_gw_error_003)
                                .show()
                        }
                    }
                }
            }
        }

        performRevoke()
    }

    private fun performRevoke() {
        ClientManager.getClient().disconnect()
        NuguOAuth.getClient().clearAuthorization()
        PreferenceHelper.credentials(this@MainActivity, "")
        LoginActivity.invokeActivity(this)
        finishAffinity()
    }

    private fun isNetworkConnected(): Boolean {
        val connMgr = this@MainActivity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo: NetworkInfo? = connMgr.activeNetworkInfo
        return activeNetworkInfo?.isConnected ?: false
    }

    private fun checkPermissionForOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 1)
            } else {
                startService()
            }
        } else {
            startService()
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (!Settings.canDrawOverlays(this)) {
                // todo. when disagree
//                finish()
            } else {
                startService()
            }
        }
    }

    override fun onStop() {
        SampleAppService.showFloating(applicationContext)
        super.onStop()
    }

    override fun onStart() {
        SampleAppService.hideFloating(applicationContext)
        super.onStart()
    }

    private fun startService() {
        SampleAppService.start(applicationContext)
    }
}
