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
import android.content.Intent
import android.net.Uri
import com.skt.nugu.sdk.client.configuration.ConfigurationStore
import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.login.exception.ClientUnspecifiedException
import com.skt.nugu.sdk.platform.android.login.view.NuguOAuthCallbackActivity
import java.lang.IllegalStateException
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
class NuguOAuth(private val OAuthServerUrl: String?) : NuguOAuthInterface, AuthDelegate, NuguOAuthClient.UrlDelegate {
    /**
     * Companion objects
     */
    companion object {
        private const val TAG = "NuguOAuth"
        private const val REQUEST_CODE_LOGIN = 10000
        private const val REQUEST_CODE_ACCOUNT = 10001

        val EXTRA_OAUTH_ACTION = "nugu.intent.extra.oauth.action"
        val EXTRA_OAUTH_THEME = "nugu.intent.extra.oauth.theme"

        val ACTION_LOGIN = "nugu.intent.action.oauth.LOGIN"
        val ACTION_ACCOUNT = "nugu.intent.action.oauth.ACCOUNT"

        private var instance: NuguOAuth? = null

        /**
         * Create a [NuguOAuth]
         * @return a [NuguOAuth] instance
         */
        fun create(
            options: NuguOAuthOptions,
            OAuthServerUrl: String? = null
        ): NuguOAuth {
            Logger.d(TAG, "[create]")
            if(instance == null) {
                instance = NuguOAuth(OAuthServerUrl)
            }
            instance?.setOptions(options)
            return instance as NuguOAuth
        }

        @Deprecated(
            level = DeprecationLevel.ERROR,
            replaceWith = ReplaceWith("NuguOAuth.getClient().setOptions(newOptions)"),
            message = "This feature is no longer supported. Using NuguOAuth.getClient() instead."
        )
        fun getClient(newOptions: NuguOAuthOptions? = null): NuguOAuth {
            throw NotImplementedError()
        }
        /**
         * Returns a NuguOAuth instance.
         */
        @Throws(IllegalStateException::class)
        fun getClient(): NuguOAuth {
            if (instance == null) {
                throw IllegalStateException(
                    "Failed to create NuguOAuth," +
                            "Using after calling NuguOAuth.create(context)"
                )
            }
            return instance as NuguOAuth
        }
    }

    private var authError: NuguOAuthError = NuguOAuthError(Throwable("An unexpected error"))

    private val executor = Executors.newSingleThreadExecutor()

    @Throws(IllegalStateException::class)
    override fun baseUrl(): String {
        val url = OAuthServerUrl ?: ConfigurationStore.configuration()?.OAuthServerUrl
        return url ?: throw IllegalStateException("Invalid server URL address")
    }

    // current state
    private var state = AuthStateListener.State.UNINITIALIZED
    private var code: String? = null
    private var refreshToken: String? = null

    // authentication Implementation
    private val client: NuguOAuthClient by lazy {
        NuguOAuthClient(this)
    }

    /// Authorization state change listeners.
    private val listeners = ConcurrentLinkedQueue<AuthStateListener>()

    /** Oauth default options @see [NuguOAuthOptions] **/
    private lateinit var options: NuguOAuthOptions

    private var onceLoginListener: OnceLoginListener? = null

    // CSRF protection
    private var clientState: String = ""

    inner class OnceLoginListener(val realListener: OnLoginListener) : OnLoginListener {
        private var called = false
        override fun onSuccess(credentials: Credentials) {
            if (called) return
            called = true
            realListener.onSuccess(credentials)
        }

        override fun onError(error: NuguOAuthError) {
            if (called) return
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
    fun loginInternal(stateListener: AuthStateListener?) {
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

        executor.submit {
            runCatching {
                checkClientId()
                checkClientSecret()
                client.handleRevoke()
            }.onSuccess {
                clearAuthorization()
                listener.onSuccess()
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
    fun setAuthorization(tokenType: String, accessToken: String) {
        Logger.d(TAG, "[setAuthorization]")
        client.getCredentials().accessToken = accessToken
        client.getCredentials().tokenType = tokenType
        setAuthState(AuthStateListener.State.REFRESHED)
    }

    /**
     * Set a [Credentials]
     */
    fun setCredentials(credential: String) = setCredentials(Credentials.parse(credential))
    fun setCredentials(credential: Credentials) = client.setCredentials(credential)

    fun getRefreshToken() = client.getCredentials().refreshToken
    fun getIssuedTime(): Long {
        val credential = client.getCredentials()
        return credential.issuedTime
    }

    fun getExpiresInMillis(): Long {
        val credential = client.getCredentials()
        return credential.expiresIn * 1000
    }

    /**
     * Check if the token is expired
     **/
    fun isExpired() = client.isExpired()

    /**
     * Check whether a user is authenticated or not
     * @return true is authorized, otherwise not authorized
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
    /**
     * Check whether a anonymous user is authenticated or not.
     * @return true is authorized, otherwise not authorized
     */
    override fun isAnonymouslyLogin(): Boolean {
        val hasAccessToken = client.getCredentials().accessToken.isNotBlank()
        val hasRefreshToken = client.getCredentials().refreshToken.isNotBlank()
        return hasAccessToken && !hasRefreshToken
    }
    /**
     * Check whether a tid user is authenticated or not
     * @return true is authorized, otherwise not authorized
     */
    override fun isTidLogin(): Boolean {
        val hasAccessToken = client.getCredentials().accessToken.isNotBlank()
        val hasRefreshToken = client.getCredentials().refreshToken.isNotBlank()
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
     * Returns true if server-initiative-directive is supported.
     **/
    override fun isSidSupported(): Boolean {
        return getScope()?.contains("device:S.I.D.") ?: false
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
     */
    override fun setOptions(options: NuguOAuthOptions) {
        this.options = options.apply {
            ConfigurationStore.configuration()?.let {
                if (clientId.isBlank()) {
                    clientId = it.clientId
                }
                if (redirectUri.isNullOrEmpty()) {
                    redirectUri = it.redirectUri
                }
                if (clientSecret.isBlank()) {
                    clientSecret = it.clientSecret
                }
            }
        }
        client.setOptions(this.options)
    }

    /**
     * Get the [NuguOAuthOptions]
     * @return the [options]
     */
    override fun getOptions() = try {
        this.options
    } catch (e: UninitializedPropertyAccessException) {
        null
    }

    private fun generateClientState() = UUID.randomUUID().toString().apply {
        clientState = this
    }
    private fun verifyState(state: String?) = state == this.clientState
    private fun verifyCode(code: String?) = !code.isNullOrBlank()

    private fun makeAuthorizeUri(theme: String) = String.format(
        "${baseUrl()}/v1/auth/oauth/authorize?response_type=code&client_id=%s&redirect_uri=%s&data=%s",
        options.clientId,
        options.redirectUri,
        URLEncoder.encode("{\"deviceSerialNumber\":\"${options.deviceUniqueId}\",\"theme\":\"$theme\"}", "UTF-8")
    )

    /**
     * Creating a login intent
     */
    fun getLoginIntent(theme : String) = Intent(Intent.ACTION_VIEW).apply {
        data = getLoginUri(theme)
    }

    /**
     * Creating a login uri
     */
    fun getLoginUri(theme : String?): Uri {
        val appendUri = String.format(
            "&state=%s", generateClientState()
        )
        return Uri.parse(makeAuthorizeUri(theme?:NuguOAuthInterface.THEME.LIGHT.name) + appendUri)
    }

    /**
     * Creating a accountinfo intent
     */
    fun getAccountInfoIntent(theme : String?) = Intent(Intent.ACTION_VIEW).apply {
        data = getAccountInfoUri(theme)
    }

    /**
     * Creating a accountinfo uri
     */
    fun getAccountInfoUri(theme: String?): Uri {
        val appendUri = String.format(
            "&prompt=%s&access_token=%s&state=%s",
            "mypage",
            client.getCredentials().accessToken,
            generateClientState()
        )
        return Uri.parse(makeAuthorizeUri(theme?:NuguOAuthInterface.THEME.LIGHT.name) + appendUri)
    }

    override fun accountWithTid(
        activity: Activity,
        listener: NuguOAuthInterface.OnAccountListener,
        theme: NuguOAuthInterface.THEME
    ) {
        runCatching {
            this.options.grantType = NuguOAuthOptions.AUTHORIZATION_CODE
            this.onceLoginListener = OnceLoginListener(listener)
            Intent(activity, NuguOAuthCallbackActivity::class.java).apply {
                putExtra(EXTRA_OAUTH_ACTION, ACTION_ACCOUNT)
                putExtra(EXTRA_OAUTH_THEME, theme.name)
                activity.startActivityForResult(this, REQUEST_CODE_ACCOUNT)
            }
        }.onFailure {
            listener.onError(NuguOAuthError(it))
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
        var state: String? = null
        var code: String? = null
        when (intent) {
            is Intent -> intent.dataString?.let {
                Uri.parse(URLDecoder.decode(it, "UTF-8")).let {
                    code = it.getQueryParameter("code")
                    state = it.getQueryParameter("state")
                }
            }
        }

        if (!verifyState(state)) {
            Logger.d(TAG, "[setCodeFromIntent] Csrf failed. state=$state")
            return false
        }
        if (!verifyCode(code)) {
            Logger.d(TAG, "[setCodeFromIntent] code is null or blank. code=$code")
            return false
        }
        this.code = code
        return true
    }

    /**
     * Start the login with tid
     * @param activity The activity making the call.
     * @param requestCode If >= 0, this code will be returned in
     *                    onActivityResult() when the activity exits
     */
    override fun loginWithTid(
        activity: Activity,
        listener: OnLoginListener,
        theme: NuguOAuthInterface.THEME
    ) {
        runCatching {
            this.options.grantType = NuguOAuthOptions.AUTHORIZATION_CODE
            this.onceLoginListener = OnceLoginListener(listener)

            checkClientId()
            checkClientSecret()
            checkRedirectUri()

            Intent(activity, NuguOAuthCallbackActivity::class.java).apply {
                putExtra(EXTRA_OAUTH_ACTION, ACTION_LOGIN)
                putExtra(EXTRA_OAUTH_THEME, theme.name)
                activity.startActivityForResult(this, REQUEST_CODE_LOGIN)
            }
        }.onFailure {
            listener.onError(NuguOAuthError(it))
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
     * Start anonymous login.
     */
    override fun loginAnonymously(listener: OnLoginListener) {
        runCatching {
            this.options.grantType = NuguOAuthOptions.CLIENT_CREDENTIALS
            checkClientId()
            checkClientSecret()
        }.onFailure {
            listener.onError(NuguOAuthError(it))
        }.onSuccess {
            loginInternal(object : AuthStateListener {
                override fun onAuthStateChanged(
                    newState: AuthStateListener.State
                ) = handleAuthState(newState, OnceLoginListener(listener))
            })
        }
    }

    /**
     * Start a login without browser.
     */
    override fun loginWithAuthenticationCode(
        code: String,
        listener: OnLoginListener
    ) {
        runCatching {
            this.options.grantType = NuguOAuthOptions.AUTHORIZATION_CODE
            this.code = code
            checkClientId()
            checkClientSecret()
        }.onFailure {
            listener.onError(NuguOAuthError(it))
        }.onSuccess {
            loginInternal(object : AuthStateListener {
                override fun onAuthStateChanged(
                    newState: AuthStateListener.State
                ) = handleAuthState(newState, OnceLoginListener(listener))
            })
        }
    }

    /**
     * Refresh Token with tid.
     */
    override fun loginSilentlyWithTid(refreshToken: String, listener: OnLoginListener) {
        runCatching {
            this.options.grantType = NuguOAuthOptions.REFRESH_TOKEN
            this.refreshToken = refreshToken

            checkClientId()
            checkClientSecret()
        }.onFailure {
            listener.onError(NuguOAuthError(it))
        }.onSuccess {
            loginInternal(object : AuthStateListener {
                override fun onAuthStateChanged(
                    newState: AuthStateListener.State
                ) = handleAuthState(newState, OnceLoginListener(listener))
            })
        }
    }

    private fun handleAuthState(
        newState: AuthStateListener.State,
        listener: OnceLoginListener
    ): Boolean {
        when (newState) {
            AuthStateListener.State.EXPIRED,
            AuthStateListener.State.UNINITIALIZED -> { /* noop */
            }
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
        runCatching {
            this.options.grantType = NuguOAuthOptions.DEVICE_CODE
            this.code = code

            checkClientId()
            checkClientSecret()
        }.onFailure {
            listener.onError(NuguOAuthError(it))
        }.onSuccess {
            loginInternal(object : AuthStateListener {
                override fun onAuthStateChanged(
                    newState: AuthStateListener.State
                ) = handleAuthState(newState, OnceLoginListener(listener))
            })
        }
    }

    override fun startDeviceAuthorization(data: String, listener: OnDeviceAuthorizationListener) {
        executor.submit {
            runCatching {
                this.options.grantType = NuguOAuthOptions.DEVICE_CODE

                checkClientId()
                checkClientSecret()
                client.handleStartDeviceAuthorization(data)
            }.onFailure {
                listener.onError(NuguOAuthError(it))
            }.onSuccess {
                listener.onSuccess(it)
            }
        }
    }

    override fun introspect(listener: NuguOAuthInterface.OnIntrospectResponseListener) {
        executor.submit {
            runCatching {
                checkClientId()
                checkClientSecret()
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

    @Deprecated(
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("isTidLogin()"),
        message = "This feature is no longer supported."
    )
    fun isAuthorizationCodeLogin(): Boolean {
        throw NotImplementedError()
    }

    @Deprecated(
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("isAnonymouslyLogin()"),
        message = "This feature is no longer supported."
    )
    fun isClientCredentialsLogin(): Boolean {
        throw NotImplementedError()
    }

    @Deprecated(
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("loginWithTid(activity, listener)"),
        message = "This feature is no longer supported."
    )
    fun loginByInAppBrowser(
        activity: Activity,
        listener: OnLoginListener,
        theme: NuguOAuthInterface.THEME = NuguOAuthInterface.THEME.LIGHT
    ) {
        throw NotImplementedError()
    }

    @Deprecated(
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("accountWithTid(activity, listener)"),
        message = "This feature is no longer supported."
    )
    fun accountByInAppBrowser(
        activity: Activity,
        listener: NuguOAuthInterface.OnAccountListener,
        theme: NuguOAuthInterface.THEME = NuguOAuthInterface.THEME.LIGHT
    ) {
        throw NotImplementedError()
    }

    @Deprecated(
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("loginAnonymously(listener)"),
        message = "This feature is no longer supported."
    )
    fun login(listener: OnLoginListener) {
        throw NotImplementedError()
    }

    @Deprecated(
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("loginSilentlyWithTid(refreshToken, listener)"),
        message = "This feature is no longer supported."
    )
    fun loginSilently(refreshToken: String, listener: OnLoginListener) {
        throw NotImplementedError()
    }

    @Deprecated(
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("introspect(listener)"),
        message = "This feature is deprecated."
    )
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

    fun deviceUniqueId() = try {
        options.deviceUniqueId
    } catch (e : UninitializedPropertyAccessException ) {
        Logger.w(TAG, "[deviceUniqueId] : $e")
        null
    }
}