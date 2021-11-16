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

/**
 * An OAuth authorization request.
 */
sealed class AuthorizationRequest(val grantType: NuguOAuthClient.GrantType) {
    class DeviceCodeRequest(val deviceCode: String) : AuthorizationRequest(NuguOAuthClient.GrantType.DEVICE_CODE)
    class RefreshTokenRequest(val refreshToken: String) : AuthorizationRequest(NuguOAuthClient.GrantType.REFRESH_TOKEN)
    class AuthorizationCodeRequest(val code: String?) : AuthorizationRequest(NuguOAuthClient.GrantType.AUTHORIZATION_CODE)
    class ClientCredentialsRequest : AuthorizationRequest(NuguOAuthClient.GrantType.CLIENT_CREDENTIALS)
}