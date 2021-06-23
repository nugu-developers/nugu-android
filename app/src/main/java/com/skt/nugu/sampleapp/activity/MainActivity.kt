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
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.navigation.NavigationView
import com.skt.nugu.sampleapp.BuildConfig
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.client.ExponentialBackOff
import com.skt.nugu.sampleapp.client.TokenRefresher
import com.skt.nugu.sampleapp.client.toResId
import com.skt.nugu.sampleapp.service.SampleAppService
import com.skt.nugu.sampleapp.utils.*
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.chips.Chip
import com.skt.nugu.sdk.agent.display.DisplayAggregatorInterface
import com.skt.nugu.sdk.agent.system.SystemAgentInterface
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.platform.android.NuguAndroidClient
import com.skt.nugu.sdk.platform.android.login.auth.Credentials
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthError
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregator
import com.skt.nugu.sdk.platform.android.speechrecognizer.SpeechRecognizerAggregatorInterface
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateFragment
import com.skt.nugu.sdk.platform.android.ux.template.presenter.TemplateRenderer
import com.skt.nugu.sdk.platform.android.ux.widget.*
import com.skt.nugu.sdk.platform.android.ux.widget.NuguButton.Companion.dpToPx

class MainActivity : AppCompatActivity(), SpeechRecognizerAggregatorInterface.OnStateChangeListener,
    NavigationView.OnNavigationItemSelectedListener, ConnectionStatusListener, SystemAgentInterfaceListener,
    TokenRefresher.Listener {
    companion object {
        private const val TAG = "MainActivity"
        private val permissions = arrayOf(
            android.Manifest.permission.RECORD_AUDIO
        )
        private const val requestCode = 100
        private const val EXTRA_WAKEUP_ACTION = "wakeupAction"

        fun invokeActivity(context: Context, wakeupAction: Boolean = false) {
            context.startActivity(Intent(context, MainActivity::class.java).putExtra(EXTRA_WAKEUP_ACTION, wakeupAction))
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
    private val toolBar: Toolbar by lazy {
        findViewById<Toolbar>(R.id.toolbar)
    }
    private val chromeWindow: ChromeWindow by lazy {
        ChromeWindow(this, findViewById<CoordinatorLayout>(R.id.coordinator), object : ChromeWindow.NuguClientProvider {
            override fun getNuguClient() = ClientManager.getClient()
        })
    }

    private val onRequestPermissionResultHandler: OnRequestPermissionResultHandler by lazy {
        OnRequestPermissionResultHandler(this)
    }

    private val speechRecognizerAggregator: SpeechRecognizerAggregator by lazy {
        ClientManager.speechRecognizerAggregator
    }

    private val tokenRefresher = TokenRefresher(NuguOAuth.getClient())

    private val fragmentLifecycleCallback = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            super.onFragmentStarted(fm, f)
            updateNuguButton()
        }

        override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
            super.onFragmentStopped(fm, f)
            updateNuguButton()
        }
    }

    private val templateListener = object : TemplateRenderer.TemplateListener {
        override fun onComplete(templateId: String, templateType: String, displayType: DisplayAggregatorInterface.Type?) {
            Log.d(TAG, "template loading complete $templateId / $displayType")
        }

        override fun onFail(templateId: String, templateType: String, displayType: DisplayAggregatorInterface.Type?, reason: String?) {
            Log.d(TAG, "template loading fail $templateId / $displayType / $reason")
        }
    }

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
            supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallback, false)

            ConfigurationStore.templateServerUri { url, error ->
                error?.apply {
                    Log.e(TAG, "[onCreate] error=$this")
                    return@templateServerUri
                }
                it.setServerUrl(url)
            }

            it.templateListener = templateListener
        })
        // add listener for system agent.
        ClientManager.getClient().addSystemAgentListener(this)

        chromeWindow.setOnChromeWindowCallback(object : ChromeWindow.OnChromeWindowCallback {
            override fun onExpandStarted() {
                updateNuguButton(voiceChromeExpandStarted = true)
            }

            override fun onHiddenFinished() {
                updateNuguButton()
                if (speechRecognizerAggregator.getState() == SpeechRecognizerAggregatorInterface.State.EXPECTING_SPEECH) {
                    speechRecognizerAggregator.stopListening()
                }
            }

            override fun onChipsClicked(item: NuguChipsView.Item) {
                ClientManager.getClient().requestTextInput(item.text)
                speechRecognizerAggregator.stopListening()
            }
        })
        chromeWindow.apply {
            setOnCustomChipsProvider(object : ChromeWindow.CustomChipsProvider {
                override fun onCustomChipsAvailable(isSpeaking: Boolean): Array<Chip>? {
                    return if (!isSpeaking) {
                        var dummyChips = arrayOf<Chip>()
                        for (index in 0 until 5) {
                            dummyChips += Chip(type = Chip.Type.values()[index % Chip.Type.values().size], text = "guide text #$index", token = null)
                        }

                        dummyChips
                    } else {
                        null
                    }
                }
            })
        }

        version.text = "v${BuildConfig.VERSION_NAME}"

        tokenRefresher.setListener(this)
        tokenRefresher.start()

        checkPermissionForOverlay()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "[onResume]")
        ClientManager.keywordResourceUpdateIfNeeded(this)
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
        updateNuguButton()

        handleExtras(intent.extras)
    }

    private fun handleExtras(extras: Bundle?) {
        if (extras?.getBoolean(EXTRA_WAKEUP_ACTION) == true) {
            intent.removeExtra(EXTRA_WAKEUP_ACTION)
            speechRecognizerAggregator.startListening(initiator = ASRAgentInterface.Initiator.TAP)
        }
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
        chromeWindow.destroy()

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

        supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallback)

        templateRenderer.templateListener = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (chromeWindow.isShown()) {
            speechRecognizerAggregator.stopListening()
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
                            speechRecognizerAggregator.startListening(initiator = ASRAgentInterface.Initiator.TAP)
                        }

                        override fun onDenied() {
                            Log.d(TAG, "[requestPermissions::onDenied]")
                        }

                        override fun onCanceled() {
                            Log.d(TAG, "[requestPermissions::onCanceled]")
                        }
                    })
            } else {
                speechRecognizerAggregator.startListening(initiator = ASRAgentInterface.Initiator.TAP)
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
                                .yOffset(dpToPx(68f, baseContext))
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

    private fun updateNuguButton(voiceChromeExpandStarted: Boolean = false) {
        fun isNuguButtonVisibleByTemplate(): Boolean {
            return supportFragmentManager.fragments.filter {
                !it.isRemoving && it.isVisible && (it as? TemplateFragment)?.isNuguButtonVisible() == true
            }.any()
        }

        fun show() {
            btnStartListening.visibility = View.VISIBLE
        }

        fun hide() {
            btnStartListening.visibility = View.INVISIBLE
        }

        val isVoiceChromeVisible = chromeWindow.isShown() || voiceChromeExpandStarted

        runOnUiThread {
            if (!PreferenceHelper.enableNugu(this)) {
                hide()
                return@runOnUiThread
            }

            btnStartListening.isEnabled = isConnected()

            when (btnStartListening.isFab()) {
                true -> {
                    if (!isVoiceChromeVisible && !isNuguButtonVisibleByTemplate()) show()
                    else hide()
                }

                false -> {
                    if (!isNuguButtonVisibleByTemplate()) show().also { btnStartListening.isActivated = isVoiceChromeVisible }
                    else hide()
                }
            }
        }
    }

    private fun isConnected(): Boolean {
        return connectionStatus == ConnectionStatusListener.Status.CONNECTED
    }

    override fun onConnectionStatusChanged(status: ConnectionStatusListener.Status, reason: ConnectionStatusListener.ChangedReason) {
        if (connectionStatus != status) {
            connectionStatus = status
            updateNuguButton()
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
                    /** Authentication failed. Please refresh your access_token. **/
                    tokenRefresher.start(forceUpdate = true)
                }
            }
        } else if (status == ConnectionStatusListener.Status.CONNECTED) {
            ExponentialBackOff.reset()
        }
    }

    override fun onRevoke(reason: SystemAgentInterface.RevokeReason) {
        Log.d(TAG, "[onRevoke] The device has been revoked ($reason)")
        handleRevoke()
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
        runOnUiThread {
            NuguToast.with(applicationContext)
                .message(error.toResId())
                .duration(NuguToast.LENGTH_SHORT)
                .show()

            if (error.error != NuguOAuthError.NETWORK_ERROR &&
                error.error != NuguOAuthError.INITIALIZE_ERROR
            ) {
                handleRevoke()
            }
        }
    }

    private fun handleRevoke() {
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
        if (PreferenceHelper.enableFloating(this)) {
            SampleAppService.showFloating(applicationContext)
        }
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
