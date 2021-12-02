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
package com.skt.nugu.sdk.agent

import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.SupportedInterfaceContextProvider

/**
 * This is a base class for CapabilityAgent which should perform following roles:
 * * should provide configurations for directives which can handle
 * * should handle directives which provided
 * * should provide an capability context's state
 */
abstract class AbstractCapabilityAgent(interfaceName: String) : CapabilityAgent
    , AbstractDirectiveHandler()
    , SupportedInterfaceContextProvider {

    final override val namespaceAndName: NamespaceAndName = NamespaceAndName(SupportedInterfaceContextProvider.NAMESPACE, interfaceName)
}