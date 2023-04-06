package com.skt.nugu.sdk.platform.android.login.auth

internal class OnceLoginListener(private var listener: NuguOAuthInterface.OnLoginListener?) :
    NuguOAuthInterface.OnLoginListener {
    override fun onSuccess(credentials: Credentials) {
        listener?.onSuccess(credentials)
        listener = null
    }
    override fun onError(error: NuguOAuthError) {
        listener?.onError(error)
        listener = null
    }
}
