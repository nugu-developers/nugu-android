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
import androidx.annotation.VisibleForTesting
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
import com.skt.nugu.sdk.platform.android.login.auth.AuthorizationRequest.ClientCredentialsRequest
import com.skt.nugu.sdk.platform.android.login.auth.AuthorizationRequest.AuthorizationCodeRequest
import com.skt.nugu.sdk.platform.android.login.auth.AuthorizationRequest.RefreshTokenRequest
import com.skt.nugu.sdk.platform.android.login.auth.AuthorizationRequest.DeviceCodeRequest
import com.skt.nugu.sdk.platform.android.login.extention.appendDataToJson

/**
 * NuguOAuth provides an implementation of the NuguOAuthInterface
 * authorization process.
 */
object NuguOAuth : NuguOAuthInterface, AuthDelegate, NuguOAuthClient.UrlDelegate {
    private const val TAG = "NuguOAuth"
    private var OAuthServerUrl: String? = null
    private var authError: NuguOAuthError = NuguOAuthError(Throwable("An unexpected error"))
    private val executor = Executors.newSingleThreadExecutor()

    // current state
    private var state = AuthStateListener.State.UNINITIALIZED
    // authentication Implementation
    private val client: NuguOAuthClient by lazy {
        NuguOAuthClient(this)
    }
    /// Authorization state change listeners.
    private val listeners = ConcurrentLinkedQueue<AuthStateListener>()

    /** Oauth default options @see [NuguOAuthOptions] **/
    private lateinit var options: NuguOAuthOptions

    @VisibleForTesting
    internal var onceLoginListener: OnceLoginListener? = null

    const val EXTRA_OAUTH_ACTION = "nugu.intent.extra.oauth.action"
    const val EXTRA_OAUTH_DATA = "nugu.intent.extra.oauth.data"

    const val ACTION_LOGIN = "nugu.intent.action.oauth.LOGIN"
    const val ACTION_ACCOUNT = "nugu.intent.action.oauth.ACCOUNT"

    /**
     * Returns a NuguOAuth instance.
     */
    fun getClient(): NuguOAuth {
        return this
    }

    /**
     * Create a [NuguOAuth]
     * @return a [NuguOAuth] instance
     */
    fun create(
        options: NuguOAuthOptions,
        serverUrl: String? = null
    ): NuguOAuth {
        Logger.d(TAG, "[create]")
        setOptions(options)
        OAuthServerUrl = serverUrl?.also {
            Logger.d(TAG, "OAuth Server URL has changed.")
        }
        return this
    }

    @Throws(IllegalStateException::class)
    override fun baseUrl(): String {
        val url = OAuthServerUrl ?: ConfigurationStore.configuration()?.OAuthServerUrl
        return url ?: throw IllegalStateException("Invalid server URL address")
    }

    override fun tokenEndpoint(): String {
        val url = if(ConfigurationStore.configuration() != null) {
            ConfigurationStore.configurationMetadataSync()?.tokenEndpoint
        } else null
        return url ?: "${baseUrl()}/v1/auth/oauth/token"
    }

    override fun authorizationEndpoint(): String {
        val url = if(ConfigurationStore.configuration() != null) {
            ConfigurationStore.configurationMetadataSync()?.authorizationEndpoint
        } else null
        return url ?: "${baseUrl()}/v1/auth/oauth/authorize"
    }

    override fun introspectionEndpoint(): String {
        val url = if(ConfigurationStore.configuration() != null) {
            ConfigurationStore.configurationMetadataSync()?.introspectionEndpoint
        } else null
        return url ?: "${baseUrl()}/v1/auth/oauth/introspect"
    }

    override fun revocationEndpoint(): String {
        val url = if(ConfigurationStore.configuration() != null) {
            ConfigurationStore.configurationMetadataSync()?.revocationEndpoint
        } else null
        return url ?: "${baseUrl()}/v1/auth/oauth/revoke"
    }

    override fun deviceAuthorizationEndpoint(): String {
        return "${baseUrl()}/v1/auth/oauth/device_authorization"
    }

    override fun meEndpoint(): String {
        return "${baseUrl()}/v1/auth/oauth/me"
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
    override fun getAuthorization(): String {
        return client.buildAuthorization()
    }

    /**
     * Request an authentication with auth code
     */
    fun loginInternal(authorizationRequest: AuthorizationRequest, stateListener: AuthStateListener?) {
        Logger.d(TAG, "[login] $authorizationRequest")

        setAuthState(AuthStateListener.State.UNINITIALIZED)

        stateListener?.let {
            removeAuthStateListener(it)
            addAuthStateListener(it)
        }
        executeAuthorization(authorizationRequest)
    }


    /**
     * Executes revoke the device in a thread.
     */
    override fun revoke(listener: NuguOAuthInterface.OnRevokeListener) {
        executor.submit {
            runCatching {
                checkClientId()
                checkClientSecret()
                client.handleRevoke()
            }.onSuccess {
                clearAuthorization()
                listener.onSuccess()
            }.onFailure {
                Logger.e(TAG, "[revoke] onFailure=$it")
                listener.onError(NuguOAuthError(it))
            }
        }
    }

    /**
     * Executes an authorization flow in a thread.
     */
    private fun executeAuthorization(authorizationRequest: AuthorizationRequest) {
        executor.submit {
            runCatching {
                client.handleAuthorizationFlow(authorizationRequest)
            }.onSuccess {
                setAuthState(AuthStateListener.State.REFRESHED)
            }.onFailure {
                Logger.e(TAG, "[executeAuthorization] onError=$it")
                // If UnAuthenticatedException in AuthorizationFlow,
                // remove existing token from cache.
                authError = NuguOAuthError(it)
                setAuthState(AuthStateListener.State.UNRECOVERABLE_ERROR)
            }
        }
    }

    /**
     * Delete a auth token
     */
    fun clearAuthorization() {
        Logger.d(TAG, "[clearAuthorization]")
        client.getCredentials().clear()
        setAuthState(AuthStateListener.State.UNINITIALIZED)
    }

    /**
     * Sets a auth token and update to the devicegateway
     * @param tokenType The token type
     * @param accessToken The access token
     */
    @Suppress("unused")
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
    fun getCredentials() = client.getCredentials()

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
            Logger.d(TAG, "Authentication failed because the accessToken was invalid, $result")
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
    @Suppress("unchecked")
    fun getScope(): String? {
        return client.getCredentials().scope
    }

    /**
     * Returns true if server-initiative-directive is supported.
     **/
    fun isSidSupported(): Boolean {
        return getScope()?.contains("device:S.I.D.") ?: false
    }

    /**
     * Gets an auth status
     * @returns the current state
     * */
    @Suppress("unused")
    fun getAuthState() = this.state

    /**
     * Set the authorization state to be reported onAuthStateChanged to listeners.
     * @param newState The new state.
     */
    @Suppress("unchecked")
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

    private val csrf = CSRFProtection()

    @VisibleForTesting
    internal fun verifyCode(code: String?) = !code.isNullOrBlank()

    @VisibleForTesting
    internal fun makeAuthorizeUri(data: String?) = String.format(
        "${authorizationEndpoint()}?response_type=code&client_id=%s&redirect_uri=%s&data=%s",
        options.clientId,
        options.redirectUri,
        URLEncoder.encode(data.toString(), "UTF-8")
    )

    /**
     * Creating a login intent
     */
    @Suppress("unused")
    fun getLoginIntent(data: String?) = Intent(Intent.ACTION_VIEW).apply {
        this.data = getLoginUri(data)
    }

    /**
     * Creating a login uri
     */
    fun getLoginUri(data : String?): Uri = Uri.parse(makeAuthorizeUri(data) + "&state=${csrf.generateState()}")

    /**
     * Creating a accountinfo intent
     */
    @Suppress("unused")
    fun getAccountInfoIntent(data: String?) = Intent(Intent.ACTION_VIEW).apply {
        this.data = getAccountInfoUri(data)
    }

    /**
     * Creating a accountinfo uri
     */
    fun getAccountInfoUri(data: String?): Uri {
        val appendUri =
            "&prompt=mypage&access_token=${client.getCredentials().accessToken}&state=${csrf.generateState()}"
        val temp = makeAuthorizeUri(data) + appendUri
        return Uri.parse(temp)
    }

    override fun accountWithTid(
        context: Context,
        listener: NuguOAuthInterface.OnAccountListener,
        data: Map<String, String>,
        theme: NuguOAuthInterface.THEME
    ) {
        runCatching {
            this.onceLoginListener = OnceLoginListener(listener)

            Intent(context, NuguOAuthCallbackActivity::class.java).apply {
                putExtra(EXTRA_OAUTH_ACTION, ACTION_ACCOUNT)
                putExtra(EXTRA_OAUTH_DATA, data.appendDataToJson(mutableMapOf(
                    "deviceSerialNumber" to options.deviceUniqueId,
                    "theme" to theme.name
                )).toString())
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(this)
            }
        }.onFailure {
            Logger.e(TAG, "[accountWithTid] onError=$it")
            listener.onError(NuguOAuthError(it))
        }
    }

    /**
     * Helper function to extract out from the onNewIntent(Intent) for Sign In.
     * @param intent
     * @return true is successful extract of authCode, otherwise false
     */
    fun verifyStateFromIntent(intent: Intent?): Boolean {
        var state: String? = null
         intent?.dataString?.let { dataString ->
            Uri.parse(URLDecoder.decode(dataString, "UTF-8")).let {
                state = it.getQueryParameter("state")
            }
        }
        return csrf.verifyState(state).also { result ->
            if(!result) {
                Logger.d(TAG, "[verifyState] The received state is invalid. Try again. state=$state")
            }
        }
    }

    fun codeFromIntent(intent: Intent?): String? {
        var code: String? = null
        intent?.dataString?.let { dataString ->
            Uri.parse(URLDecoder.decode(dataString, "UTF-8")).let {
                code = it.getQueryParameter("code")
            }
        }
        return code.also {
            if (!verifyCode(code)) {
                Logger.d(TAG, "[setCodeFromIntent] code is null or blank. code=$code")
            }
        }
    }

    /**
     * Start the login with tid
     * @param context a context
     * @param listener The listener to notify
     * @param data The extensible information
     * @param theme Theme to apply
     */
    override fun loginWithTid(
        context: Context,
        listener: OnLoginListener,
        data: Map<String, String>,
        theme: NuguOAuthInterface.THEME
    ) {
        runCatching {
            this.onceLoginListener = OnceLoginListener(listener)

            checkClientId()
            checkClientSecret()
            checkRedirectUri()

            Intent(context, NuguOAuthCallbackActivity::class.java).apply {
                putExtra(EXTRA_OAUTH_ACTION, ACTION_LOGIN)
                putExtra(EXTRA_OAUTH_DATA, data.appendDataToJson(mutableMapOf(
                    "deviceSerialNumber" to options.deviceUniqueId,
                    "theme" to theme.name
                )).toString())
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(this)
            }
        }.onFailure {
            Logger.e(TAG, "[loginWithTid] onFailure=$it")
            listener.onError(NuguOAuthError(it))
        }
    }

    /**
     * Determine success.
     * @param result true is success, otherwise false
     **/
    fun setResult(result: Boolean) {
        setResult(result, authError)
    }

    /**
     * Determine success.
     * @param result true is success, otherwise false
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
            checkClientId()
            checkClientSecret()
        }.onFailure {
            Logger.e(TAG, "[loginAnonymously] onFailure=$it")
            listener.onError(NuguOAuthError(it))
        }.onSuccess {
            loginInternal(ClientCredentialsRequest(),
                object : AuthStateListener {
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
            checkClientId()
            checkClientSecret()
        }.onFailure {
            Logger.e(TAG, "[loginWithAuthenticationCode] onFailure=$it")
            listener.onError(NuguOAuthError(it))
        }.onSuccess {
            loginInternal(AuthorizationCodeRequest(code), object : AuthStateListener {
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
            checkClientId()
            checkClientSecret()
        }.onFailure {
            Logger.e(TAG, "[loginSilentlyWithTid] onFailure=$it")
            listener.onError(NuguOAuthError(it))
        }.onSuccess {
            loginInternal(RefreshTokenRequest(refreshToken), object : AuthStateListener {
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
            AuthStateListener.State.UNINITIALIZED
            -> { /* noop */
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
        deviceCode: String,
        listener: OnLoginListener
    ) {
        runCatching {
            checkClientId()
            checkClientSecret()
        }.onFailure {
            Logger.e(TAG, "[loginWithDeviceCode] onFailure=$it")
            listener.onError(NuguOAuthError(it))
        }.onSuccess {
            loginInternal(DeviceCodeRequest(deviceCode), object : AuthStateListener {
                override fun onAuthStateChanged(
                    newState: AuthStateListener.State
                ) = handleAuthState(newState, OnceLoginListener(listener))
            })
        }
    }

    override fun startDeviceAuthorization(data: String, listener: OnDeviceAuthorizationListener) {
        executor.submit {
            runCatching {
                checkClientId()
                checkClientSecret()
                client.handleStartDeviceAuthorization(data)
            }.onFailure {
                Logger.e(TAG, "[startDeviceAuthorization] onFailure=$it")
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
                Logger.e(TAG, "[introspect] onFailure=$it")
                listener.onError(NuguOAuthError(it))
            }
        }
    }

    @VisibleForTesting
    internal fun checkRedirectUri() {
        if ("YOUR REDIRECT URI SCHEME://YOUR REDIRECT URI HOST" == options.redirectUri) {
            throw ClientUnspecifiedException(
                "Edit your application's strings.xml file, " +
                        "<string name=\"nugu_redirect_scheme\">YOUR REDIRECT URI SCHEME</string>\n" +
                        "<string name=\"nugu_redirect_host\">YOUR REDIRECT URI HOST</string>"
            )
        }
    }

    @VisibleForTesting
    internal fun checkClientSecret() {
        if ("YOUR_CLIENT_SECRET_HERE" == options.clientSecret) {
            throw ClientUnspecifiedException(
                "The clientSecret value is Wrong to YOUR_CLIENT_SECRET_HERE." +
                        "Please check The clientSecret. "
            )
        }
    }

    @VisibleForTesting
    internal fun checkClientId() {
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
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("introspect(listener)"),
        message = "This feature is deprecated."
    )
    override fun requestMe(listener: NuguOAuthInterface.OnMeResponseListener) {
        Logger.i(TAG, "[requestMe] This feature is deprecated")
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
    } catch (e: UninitializedPropertyAccessException) {
        Logger.w(TAG, "[deviceUniqueId] : $e")
        null
    }
}