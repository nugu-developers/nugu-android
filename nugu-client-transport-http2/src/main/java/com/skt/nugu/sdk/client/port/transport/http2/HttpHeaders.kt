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
package com.skt.nugu.sdk.client.port.transport.http2

internal class HttpHeaders {
    companion object {
        val CONTENT_TYPE = "Content-Type"
        val CONTENT_LENGTH = "Content-Length"
        val DIALOG_REQUEST_ID = "Dialog-Request-Id"
        val MESSAGE_ID = "Message-Id"
        val PARENT_MESSAGE_ID = "Parent-Message-Id"
        val REFERRER_DIALOG_REQUEST_ID = "Referrer-Dialog-Request-Id"
        val NAMESPACE = "Namespace"
        val VERSION = "Version"
        val FILENAME = "Filename"
        val NAME = "Name"
        val EVENT = "event"
        val ATTACHMENT = "attachment"
        val MULTIPART_RELATED = "multipart/related"
        val APPLICATION_JSON = "application/json"
        val APPLICATION_JSON_UTF8 = "application/json; charset=UTF-8"
        val APPLICATION_OPUS = "audio/opus"
        val APPLICATION_SPEEX = "audio/speex"
    }
}