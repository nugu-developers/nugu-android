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
package com.skt.nugu.sdk.platform.android.login.net

data class Request (
    val uri: String,
    val form: FormEncodingBuilder,
    val method: String,
    val headers: Headers?) {

    data class Builder(
        private val uri: String,
        private val form: FormEncodingBuilder = FormEncodingBuilder(),
        private val method: String = "POST",
        private val headers: Headers? = null
    ) {
        /**
         * Create a Request applied given params.
         */
        fun build(): Request =
            Request(
                uri,
                form,
                method,
                headers
            )
    }
}