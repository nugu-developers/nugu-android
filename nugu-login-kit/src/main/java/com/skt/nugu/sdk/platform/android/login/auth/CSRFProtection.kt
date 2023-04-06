package com.skt.nugu.sdk.platform.android.login.auth

import java.util.*

class CSRFProtection {
    var state: String? = null
    fun generateState() = UUID.randomUUID().toString().apply {
        state = this
    }
    fun verifyState(state: String?) = state?.let { it == this.state } ?: false
}