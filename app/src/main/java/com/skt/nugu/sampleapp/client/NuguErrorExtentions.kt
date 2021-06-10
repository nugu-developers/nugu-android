package com.skt.nugu.sampleapp.client

import com.skt.nugu.sampleapp.R
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthError

/**
 * The response errors return a description as defined in the spec: [https://developers-doc.nugu.co.kr/nugu-sdk/authentication]
 */
fun NuguOAuthError.toResId(): Int {
    return when (code) {
        NuguOAuthError.USER_ACCOUNT_CLOSED -> {
            R.string.code_user_account_closed
        }
        NuguOAuthError.USER_ACCOUNT_PAUSED -> {
            R.string.code_user_account_paused
        }
        NuguOAuthError.USER_DEVICE_DISCONNECTED -> {
            R.string.code_user_device_disconnected
        }
        NuguOAuthError.USER_DEVICE_UNEXPECTED -> {
            R.string.code_user_device_unexpected
        }
        else -> {
            when (error) {
                NuguOAuthError.UNAUTHORIZED -> {
                    R.string.error_unauthorized
                }
                NuguOAuthError.UNAUTHORIZED_CLIENT -> {
                    R.string.error_unauthorized_client
                }
                NuguOAuthError.INVALID_TOKEN -> {
                    R.string.error_invalid_token
                }
                NuguOAuthError.INVALID_CLIENT -> {
                    if (description == NuguOAuthError.FINISHED) {
                        R.string.service_finished
                    } else {
                        R.string.error_invalid_client
                    }
                }
                NuguOAuthError.ACCESS_DENIED -> {
                    R.string.error_access_denied
                }
                NuguOAuthError.NETWORK_ERROR -> {
                    httpCode?.let {
                        R.string.device_gw_error_002
                    } ?: R.string.device_gw_error_001
                }
                else -> {
                    R.string.device_gw_error_003
                }
            }
        }
    }
}
