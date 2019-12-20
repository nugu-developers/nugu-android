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
 * This interface is used to observe changes to the state of authorization.
 */
interface AuthStateListener {
    /**
     * The enum State describes the state of authorization.
     */
    enum class State {
        /**
         * Authorization has not yet been acquired
         */
        UNINITIALIZED,
        /**
         * Authorization has been refreshed
         */
        REFRESHED,
        /**
         * Authorization has expired
         */
        EXPIRED,
        /**
         * Authorization has failed in a manner that cannot be corrected by retrying.
         */
        UNRECOVERABLE_ERROR
    }

    /**
    * Notification that an authorization state has changed.
    * @param newState The new state of the authorization token.
    * @return if returns false then it will be removed after it is called.
    * */
    fun onAuthStateChanged(newState: State) : Boolean
}
