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

package com.skt.nugu.sdk.core.interfaces.context

import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName

abstract class OsContextProvider: ClientContextProvider {
    enum class Type(val value: String) {
        ANDROID("Android"),
        IOS("iOS"),
        LINUX("Linux")
    }

    final override fun getName(): String = "os"

    final override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        contextSetter.setState(
            namespaceAndName,
            object: ContextState {
                override fun toFullJsonString(): String = "\"${getType().value}\""
                override fun toCompactJsonString(): String = toFullJsonString()
            },
            StateRefreshPolicy.NEVER,
            stateRequestToken
        )
    }

    abstract fun getType(): Type
}