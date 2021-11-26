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
package com.skt.nugu.sdk.core.interfaces.context

import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName

interface ContextSetterInterface {
    enum class SetStateResult {
        SUCCESS,
        STATE_PROVIDER_NOT_REGISTERED,
        STATE_TOKEN_OUTDATED
    }

    companion object {
        const val FORCE_SET_TOKEN: Int = 0
    }

    /**
     * Set a [state]
     *
     * @param namespaceAndName the namespace and name of state
     * @param state the state string formatted json
     * @param refreshPolicy the refresh policy
     * @param stateRequestToken the token which should be used to update state.
     * * If call at [ContextStateProvider.provideState], use stateRequestToken given.
     * * (to set state unconditionally, default: [FORCE_SET_TOKEN])
     */
    fun setState(
        namespaceAndName: NamespaceAndName,
        state: BaseContextState,
        refreshPolicy: StateRefreshPolicy,
        type: ContextType,
        stateRequestToken: Int = FORCE_SET_TOKEN
    ): SetStateResult

//    fun setState(
//        namespaceAndName: NamespaceAndName,
//        state: String,
//        refreshPolicy: StateRefreshPolicy,
//        stateRequestToken: Int = 0,
//        type: ContextType
//    ): SetStateResult
}