/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.core.interfaces.session

/**
 * Interface to manage sessions.
 */
interface SessionManagerInterface {
    /**
     * the class for Session
     */
    data class Session(
        /**
         * the session id.
         */
        val sessionId: String,
        /**
         * the playServiceId
         */
        val playServiceId: String
    )

    /**
     * The listener for session (de)activation
     */
    interface Listener {
        /**
         * Called when session activated
         *
         * @param key the session's key. Usually, dialogRequestId of directive which set session.
         * @param session the session info
         */
        fun onSessionActivated(key: String, session: Session)

        /**
         * Called when session deactivated
         *
         * @param key the session's key. Usually, dialogRequestId of directive which set session.
         * @param session the session info
         */
        fun onSessionDeactivated(key: String, session: Session)
    }

    /**
     * the marker interface which request (de)activation for Session
     */
    interface Requester

    /**
     * Set a session with given key.
     * But, the session will not activated until [activate] called with [Requester].
     * @param key the session's key ([com.skt.nugu.sdk.core.interfaces.message.Header.dialogRequestId])
     * @param session the session which to be set
     */
    fun set(key: String, session: Session)

    /**
     * request activate a session having key.
     * @param key the session's key ([com.skt.nugu.sdk.core.interfaces.message.Header.dialogRequestId])
     * @param requester the requester which request activate for session
     */
    fun activate(key: String, requester: Requester)

    /**
     * request deactivate a session which is key.
     * @param key the session's key ([com.skt.nugu.sdk.core.interfaces.message.Header.dialogRequestId])
     * @param requester the requester which request deactivate for session
     */
    fun deactivate(key: String, requester: Requester)

    /**
     * Get active sessions.
     * @return active sessions
     */
    fun getActiveSessions(): Map<String, Session>

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)
}