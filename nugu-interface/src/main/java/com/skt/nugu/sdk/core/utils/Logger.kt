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
package com.skt.nugu.sdk.core.utils

import com.skt.nugu.sdk.core.interfaces.log.LogInterface

object Logger: LogInterface {
    var logger: LogInterface? = null

    override fun d(tag: String, msg: String, throwable: Throwable?) {
        try {
            logger?.d(tag, msg, throwable)
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        try {
            logger?.e(tag, msg, throwable)
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }

    override fun w(tag: String, msg: String, throwable: Throwable?) {
        try {
            logger?.w(tag, msg, throwable)
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }

    override fun i(tag: String, msg: String, throwable: Throwable?) {
        try {
            logger?.i(tag, msg, throwable)
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }
}