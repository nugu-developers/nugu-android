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

/**
 * Interface for context state provider
 */
interface ContextStateProvider {
    /**
     * The namespace and name of context state which provider will provide.
     */
    val namespaceAndName: NamespaceAndName

    /**
     * Called by [ContextManagerInterface] when [ContextRequester] request context.
     * Implementer should update [contextSetter] using given token([stateRequestToken])
     * @param contextSetter the context setter which is used to update state
     * @param namespaceAndName the namespace and name to be updated.
     * @param stateRequestToken the token which should be used to update state.
     */
    fun provideState(contextSetter: ContextSetterInterface, namespaceAndName: NamespaceAndName, contextType: ContextType, stateRequestToken: Int)
}