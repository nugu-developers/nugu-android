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

interface ContextGetterInterface {
    fun getContext(contextRequester: ContextRequester, target: NamespaceAndName? = null, given: HashMap<NamespaceAndName, BaseContextState>? = null, timeoutInMillis: Long = 10000L)
}

interface ContextStateProviderRegistry {
    fun setStateProvider(namespaceAndName: NamespaceAndName, stateProvider: ContextStateProvider?)
}

interface ContextManagerInterface
    : ContextSetterInterface,
    ContextGetterInterface,
    ContextStateProviderRegistry