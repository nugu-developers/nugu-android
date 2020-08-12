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
package com.skt.nugu.sdk.core.interfaces.directive

import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName

/**
 * This provides interface for directive handling results.
 * If there are directive blocked by this, should be handled after [setCompleted] or [setFailed] called.
 */
interface DirectiveHandlerResult {
    companion object {
        val POLICY_CANCEL_ALL = CancelPolicy(cancelAll = true)
        val POLICY_CANCEL_NONE = CancelPolicy(cancelAll = false)
    }

    /**
     * Should be called when directive handled successfully by [DirectiveHandler].
     */
    fun setCompleted()

    /**
     * Should be called when directive handling failed by [DirectiveHandler].
     * @param description the description for failure.
     * @param cancelPolicy the policy for cancellation
     */
    fun setFailed(description: String, cancelPolicy: CancelPolicy = POLICY_CANCEL_ALL)

    data class CancelPolicy(
        val cancelAll: Boolean = true,
        val partialTargets: Set<NamespaceAndName>? = null
    )
}