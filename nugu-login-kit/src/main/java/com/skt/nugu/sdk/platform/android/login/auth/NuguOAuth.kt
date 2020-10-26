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
package com.skt.nugu.sdk.platform.android.login.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.login.exception.ClientUnspecifiedException
import com.skt.nugu.sdk.platform.android.login.utils.PackageUtils
import com.skt.nugu.sdk.platform.android.login.view.NuguOAuthCallbackActivity
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthInterface.OnLoginListener as OnLoginListener
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthInterface.OnDeviceAuthorizationListener as OnDeviceAuthorizationListener

/**
 * NuguOAuth provides an implementation of the NuguOAuthInterface
 * authorization process.
 */
class NuguOAuth private constructor(
    private val context: Context,
    private val authServerBaseUrl: String
) : NuguOAuthInterface,
    AuthDelegate {
    /**
     * Companion objects
     */
    companion object {
        private const val TAG = "NuguOAuth"
        private const val REQUEST_LOGIN = 2019
        private const val REQUEST_ACCOUNT = 2020

        private const val BASE_AUTH_URL = "https://api.sktnugu.com"

        internal const val KEY_CLIENT_ID = "com.skt.nugu.CLIENT_ID"
        internal const val KEY_CLIENT_SECRET = "com.skt.nugu.CLIENT_SECRET"
        internal const val KEY_REDIRECT_SCHEME = "nugu_redirect_scheme"
        internal const val KEY_REDIRECT_HOST = "nugu_redirect_host"

        val EXTRA_OAUTH_ACTION = "nugu.intent.extra.oauth.action"
        val ACTION_LOGIN = "nugu.intent.action.oauth.LOGIN"
        val ACTION_ACCOUNT = "nugu.intent.action.oauth.ACCOUNT"

        private var instance: NuguOAuth? = null


        /**
         * Create a [NuguOAuth]
         * @return a [NuguOAuth] instance
         */
        fun create(
            context: Context,
            authServerBaseUrl: String = BASE_AUTH_URL
        ): NuguOAuth {
            Logger.d(TAG, "[create]")
            if(instance == null) {
                instance = NuguOAuth(context, authServerBaseUrl)
            }
            return instance as NuguOAuth
        }

        /**
         * Returns a NuguOAuth instance.
         * @param Set [NuguOAuthOptions]
         */
        fun getClient(options: NuguOAuthOptions? = null): NuguOAuth {
            if (instance == null) {
                throw ExceptionInInitializerError(
                    "Failed to create NuguOAuth," +
                            "Using after calling NuguOAuth.create(context)"
                )
            }
            options?.let {
                instance!!.setOptions(it)
            }
            return instance as NuguOAuth
        }
    }

    private var authError: NuguOAuthError = NuguOAuthError(Throwable("An unexpected error"))

    private val executor = Executors.newSingleThreadExecutor()
    // current state
    private var state = AuthStateListener.State.UNINITIALIZED
    private var code: String? = null
    private var refreshToken: String? = null
    // authentication Implementation
    private val client: NuguOAuthClient by lazy {
        NuguOAuthClient(authServerBaseUrl)
    }
    /// Authorization state change listeners.
    private val listeners = ConcurrentLinkedQueue<AuthStateListener>()
    /** Oauth default options @see [NuguOAuthOptions] **/
    lateinit var options: NuguOAuthOptions

    private var onceLoginListener: OnceLoginListener? = null

    private val authorizeUrl = "${authServerBaseUrl}/v1/auth/oauth/authorize"
    // CSRF protection
    private var clientState: String = ""

    inner class OnceLoginListener(val realListener : OnLoginListener) : OnLoginListener {
        private var called = false
        override fun onSuccess(credentials: Credentials) {
            if(called) return
            called = true
            realListener.onSuccess(credentials)
        }

        override fun onError(error: NuguOAuthError) {
            if(called) return
            called = true
            realListener.onError(error)
        }
    }
    /**
     * addAuthStateListener adds an AuthStateListener on the given was changed
     */
    override fun addAuthStateListener(listener: AuthStateListener) {
        listeners.add(listener)
    }

    /**
     * Removes an AuthStateListener
     */
    override fun removeAuthStateListener(listener: AuthStateListener) {
        listeners.remove(listener)
    }

    /**
     * Gets an authorization from cache
     */
    override fun getAuthorization(): String? {
        return client.buildAuthorization()
    }

    /**
     * Request an authentication with auth code
     */
    fun login(stateListener: AuthStateListener?) {
        Logger.d(TAG, "[login]")

        setAuthState(AuthStateListener.State.UNINITIALIZED)

        stateListener?.let {
            removeAuthStateListener(it)
            addAuthStateListener(it)
        }
        executeAuthorization()
    }


    /**
     * Executes revoke the device in a thread.
     */
    override fun revoke(listener: NuguOAuthInterface.OnRevokeListener) {
        Logger.d(TAG, "[revoke]")

        checkClientId()
        checkClientSecret()

        executor.submit {
            runCatching {
                client.handleRevoke()
            }.onSuccess {
                clearAuthorization()
                listener.onSuccess()
            }.onFailure {
                listener.onError(NuguOAuthError(it))
            }
        }
    }

    override fun requestMe(listener: NuguOAuthInterface.OnMeResponseListener) {
        Logger.d(TAG, "[requestMe]")
        executor.submit {
            runCatching {
                client.handleMe()
            }.onSuccess {
                listener.onSuccess(it)
            }.onFailure {
                listener.onError(NuguOAuthError(it))
            }
        }
    }

    /**
     * Executes an authorization flow in a thread.
     */
    private fun executeAuthorization() {
        executor.submit {
            runCatching {
                client.handleAuthorizationFlow(
                    code = code,
                    refreshToken = refreshToken
                )
            }.onSuccess {
                setAuthState(AuthStateListener.State.REFRESHED)
            }.onFailure {
                // If UnAuthenticatedException in AuthorizationFlow,
                // remove existing token from cache.
                authError = NuguOAuthError(it)
                setAuthState(AuthStateListener.State.UNRECOVERABLE_ERROR)
            }
        }
    }

    /**
     * Delete a auth token
     * @param token The auth token
     */
    fun clearAuthorization() {
        Logger.d(TAG, "[clearAuthorization]")
        client.getCredentials().clear()
        setAuthState(AuthStateListener.State.UNINITIALIZED)
    }

    /**
     * Sets a auth token and update to the devicegateway
     * @param token The auth token
     */
    fun setAuthorization(tokenType : String, accessToken: String) {
        Logger.d(TAG, "[setAuthorization]")
        client.getCredentials().accessToken = accessToken
        client.getCredentials().tokenType = tokenType
        setAuthState(AuthStateListener.State.REFRESHED)
    }
    /**
     * Set a [Credentials]
     */
    fun setCredentials(credential : String) = setCredentials(Credentials.parse(credential))
    fun setCredentials(credential : Credentials) = client.setCredentials(credential)

    fun getRefreshToken() = client.getCredentials().refreshToken
    fun getIssuedTime() : Long {
        val credential = client.getCredentials()
        return credential.issuedTime
    }
    fun getExpiresInMillis() : Long {
        val credential = client.getCredentials()
        return credential.expiresIn * 1000
    }
    /**
     * Check if the token is expired
     **/
    fun isExpired() = client.isExpired()

    /**
     * Check whether a user is authenticated or not
     * @return trus is authorized, otherwise not authorized
     */
    override fun isLogin(): Boolean {
        val result = client.getCredentials().accessToken != ""
        // Check if we need to refresh the access token to request the api
        if (result && client.isExpired()) {
            Logger.d(TAG, "Authentication failed because the accessToken was invalid, ${result}")
            setAuthState(AuthStateListener.State.EXPIRED)
        }
        return result
    }

    override fun isClientCredentialsLogin(): Boolean {
        val hasAccessToken = client.getCredentials().accessToken != ""
        val hasRefreshToken = client.getCredentials().refreshToken != ""
        return hasAccessToken && !hasRefreshToken
    }

    override fun isAuthorizationCodeLogin(): Boolean {
        val hasAccessToken = client.getCredentials().accessToken != ""
        val hasRefreshToken = client.getCredentials().refreshToken != ""
        return hasAccessToken && hasRefreshToken
    }

    /**
     * Get a scope
     * @returns the scope
     * */
    fun getScope(): String? {
        return client.getCredentials().scope
    }
    /**
     * Gets an auth status
     * @returns the current state
     * */
    fun getAuthState() = this.state

    /**
     * Set the authorization state to be reported onAuthStateChanged to listeners.
     * @param newState The new state.
     */
    fun setAuthState(newState: AuthStateListener.State) {
        if (newState == this.state) {
            return
        }
        this.state = newState

        // notify state change
        listeners.forEach {
            val consumedEvent = it.onAuthStateChanged(newState)
            if (!consumedEvent) {
                removeAuthStateListener(it)
            }
        }
    }


    /**
     * Set the [NuguOAuthOptions]
     * @returns the current state
     * */
    override fun setOptions(options: Any) {
        when (options) {
            is NuguOAuthOptions -> {
                this.options = options
            }
        }

        this.options.apply {
            if (clientId.isNullOrEmpty()) {
                clientId = PackageUtils.getMetaData(context, KEY_CLIENT_ID)
            }
            if (redirectUri.isNullOrEmpty()) {
                redirectUri = PackageUtils.getString(context, KEY_REDIRECT_SCHEME) + "://" + PackageUtils.getString(context, KEY_REDIRECT_HOST)
            }
        }
        client.setOptions(this.options)
    }
    fun generateClientState() = UUID.randomUUID().toString().apply {
        clientState = this
    }
    fun verifyState(state : String?) = state == this.clientState
    fun verifyCode(code : String?) = !code.isNullOrBlank()

    private fun makeAuthorizeUri() = String.format(
            authorizeUrl + "?response_type=code&client_id=%s&redirect_uri=%s&data=%s",
            options.clientId,
            options.redirectUri,
            URLEncoder.encode("{\"deviceSerialNumber\":\"${options.deviceUniqueId}\"}", "UTF-8")
    )
    /**
     * Creating a login intent
     */
    fun getLoginIntent() = Intent(Intent.ACTION_VIEW).apply {
        data = getLoginUri()
    }
    /**
     * Creating a login uri
     */
    fun getLoginUri() : Uri {
        val appendUri = String.format(
            "&state=%s", generateClientState()
        )
        return Uri.parse(makeAuthorizeUri() + appendUri)
    }
    /**
     * Creating a accountinfo intent
     */
    fun getAccountInfoIntent() = Intent(Intent.ACTION_VIEW).apply {
        data = getAccountInfoUri()
    }
    /**
     * Creating a accountinfo uri
     */
    fun getAccountInfoUri() : Uri {
        val appendUri = String.format(
            "&prompt=%s&access_token=%s&state=%s",
            "mypage",
            client.getCredentials().accessToken,
            generateClientState()
        )
        return Uri.parse(makeAuthorizeUri() + appendUri)
    }

    override fun accountByInAppBrowser(activity: Activity) {
        Intent(activity, NuguOAuthCallbackActivity::class.java).apply {
            putExtra(EXTRA_OAUTH_ACTION, ACTION_ACCOUNT)
            activity.startActivityForResult(this, REQUEST_ACCOUNT)
        }
    }

    override fun accountByInAppBrowser(activity: Activity, listener: NuguOAuthInterface.OnAccountListener) {
        this.options.grantType = NuguOAuthOptions.AUTHORIZATION_CODE
        this.onceLoginListener = OnceLoginListener(listener)
        Intent(activity, NuguOAuthCallbackActivity::class.java).apply {
            putExtra(EXTRA_OAUTH_ACTION, ACTION_ACCOUNT)
            activity.startActivityForResult(this, REQUEST_ACCOUNT)
        }
    }

    @Deprecated("Use setCodeFromIntent")
    fun hasAuthCodeFromIntent(intent: Any) = setCodeFromIntent(intent)

    /**
     * Helper function to extract out from the onNewIntent(Intent) for Sign In.
     * @param intent
     * @return true is successful extract of authCode, otherwise false
     */
    fun setCodeFromIntent(intent: Any): Boolean {
        var state : String? = null
        var code : String? = null
        when (intent) {
            is Intent -> intent.dataString?.let {
                Uri.parse(URLDecoder.decode(it, "UTF-8")).let {
                    code = it.getQueryParameter("code")
                    state = it.getQueryParameter("state")
                }
            }
        }

        if(!verifyState(state)) {
            Logger.d(TAG, "[setCodeFromIntent] Csrf failed. state=$state")
            return false
        }
        if(!verifyCode(code)) {
            Logger.d(TAG, "[setCodeFromIntent] code is null or blank. code=$code")
            return false
        }
        this.code = code
        return true
    }

    /**
     * Start the login with activity
     * supported type1
     * @param activity The activity making the call.
     * @param requestCode If >= 0, this code will be returned in
     *                    onActivityResult() when the activity exits
     */
    override fun loginByInAppBrowser(
        activity: Activity,
        listener: OnLoginListener
    ) {
        this.options.grantType = NuguOAuthOptions.AUTHORIZATION_CODE
        this.onceLoginListener = OnceLoginListener(listener)

        checkClientId()
        checkClientSecret()
        checkRedirectUri()

        Intent(activity, NuguOAuthCallbackActivity::class.java).apply {
            putExtra(EXTRA_OAUTH_ACTION, ACTION_LOGIN)
            activity.startActivityForResult(this, REQUEST_LOGIN)
        }
    }

    /**
     * Determine success.
     * @param true is success, otherwise false
     **/
    fun setResult(result: Boolean) {
        setResult(result, authError)
    }
    /**
     * Determine success.
     * @param true is success, otherwise false
     * @param error the NuguOAuthError
     **/
    fun setResult(result: Boolean, error: NuguOAuthError) {
        if (result) {
            onceLoginListener?.onSuccess(client.getCredentials())
        } else {
            onceLoginListener?.onError(error)
        }
    }

    /**
     * Only Type2
     */
    override fun login(listener: OnLoginListener) {
        this.options.grantType = NuguOAuthOptions.CLIENT_CREDENTIALS

        checkClientId()
        checkClientSecret()

        this.login(object : AuthStateListener {
            override fun onAuthStateChanged(
                newState: AuthStateListener.State
            ) = handleAuthState(newState, OnceLoginListener(listener))
        })
    }

    /**
     * Only Type1
     */
    override fun loginWithAuthenticationCode(
        code: String,
        listener: OnLoginListener
    ) {
        this.options.grantType = NuguOAuthOptions.AUTHORIZATION_CODE
        this.code = code

        checkClientId()
        checkClientSecret()

        this.login(object : AuthStateListener {
            override fun onAuthStateChanged(
                newState: AuthStateListener.State
            ) = handleAuthState(newState, OnceLoginListener(listener))
        })
    }

    /**
     * Only Type1
     */
    override fun loginSilently(refreshToken: String, listener: OnLoginListener) {
        this.options.grantType = NuguOAuthOptions.REFRESH_TOKEN
        this.refreshToken = refreshToken

        checkClientId()
        checkClientSecret()

        this.login(object : AuthStateListener {
            override fun onAuthStateChanged(
                newState: AuthStateListener.State
            ) = handleAuthState(newState, OnceLoginListener(listener))
        })
    }

    private fun handleAuthState(newState: AuthStateListener.State, listener: OnceLoginListener) : Boolean {
        when(newState) {
            AuthStateListener.State.EXPIRED,
            AuthStateListener.State.UNINITIALIZED -> { /* noop */}
            AuthStateListener.State.REFRESHED -> {
                /* Authentication successful */
                listener.onSuccess(client.getCredentials())
                return false
            }
            AuthStateListener.State.UNRECOVERABLE_ERROR -> {
                /* Authentication error */
                listener.onError(authError)
                return false
            }
        }
        return true
    }

    override fun loginWithDeviceCode(
        code: String,
        listener: OnLoginListener
    ) {
        this.options.grantType = NuguOAuthOptions.DEVICE_CODE
        this.code = code

        checkClientId()
        checkClientSecret()

        this.login(object : AuthStateListener {
            override fun onAuthStateChanged(
                newState: AuthStateListener.State
            ) = handleAuthState(newState, OnceLoginListener(listener))
        })
    }

    override fun startDeviceAuthorization(data: String, listener: OnDeviceAuthorizationListener) {
        this.options.grantType = NuguOAuthOptions.DEVICE_CODE

        checkClientId()
        checkClientSecret()

        executor.submit {
            runCatching {
                client.handleStartDeviceAuthorization(data)
            }.onSuccess {
                listener.onSuccess(it)
            }.onFailure {
                listener.onError(NuguOAuthError(it))
            }
        }
    }

    @Deprecated("Use introspect")
    fun getMe(data: String, listener: OnDeviceAuthorizationListener) {

        checkClientId()
        checkClientSecret()

        executor.submit {
            runCatching {
                client.handleStartDeviceAuthorization(data)
            }.onSuccess {
                listener.onSuccess(it)
            }.onFailure {
                listener.onError(NuguOAuthError(it))
            }
        }
    }

    override fun introspect(listener: NuguOAuthInterface.OnIntrospectResponseListener) {
        checkClientId()
        checkClientSecret()

        executor.submit {
            runCatching {
                client.handleIntrospect()
            }.onSuccess {
                listener.onSuccess(it)
            }.onFailure {
                listener.onError(NuguOAuthError(it))
            }
        }
    }

    private fun checkRedirectUri() {
        if ("YOUR REDIRECT URI SCHEME://YOUR REDIRECT URI HOST" == options.redirectUri) {
            throw ClientUnspecifiedException(
                "Edit your application's strings.xml file, " +
                        "<string name=\"nugu_redirect_scheme\">YOUR REDIRECT URI SCHEME</string>\n" +
                        "<string name=\"nugu_redirect_host\">YOUR REDIRECT URI HOST</string>"
            )
        }
    }

    private fun checkClientSecret() {
        if ("YOUR_CLIENT_SECRET_HERE" == options.clientSecret) {
            throw ClientUnspecifiedException(
                "The clientSecret value is Wrong to YOUR_CLIENT_SECRET_HERE." +
                        "Please check The clientSecret. "
            )
        }
    }

    private fun checkClientId() {
        if ("YOUR_CLIENT_ID_HERE" == options.clientId) {
            throw ClientUnspecifiedException(
                "Edit your application's AndroidManifest.xml file, " +
                        "and add the following declaration within the <application> element.\n\n" +
                        "<meta-data\n" +
                        "                android:name=\"com.skt.nugu.CLIENT_ID\"\n" +
                        "                android:value=\"YOUR_CLIENT_ID_HERE\" />"
            )
        }
    }
}