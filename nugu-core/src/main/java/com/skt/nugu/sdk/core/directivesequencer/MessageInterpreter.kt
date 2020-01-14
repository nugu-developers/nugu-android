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
package com.skt.nugu.sdk.core.directivesequencer

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.core.interfaces.message.MessageObserver
import com.skt.nugu.sdk.core.interfaces.attachment.AttachmentManagerInterface
import com.skt.nugu.sdk.core.interfaces.message.AttachmentMessage
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.interfaces.message.Header
import com.skt.nugu.sdk.core.utils.Logger


/**
 * Class that convert an message to [Directive] or [AttachmentMessage],
 * and then directives will be passed to [DirectiveGroupProcessor],
 * attachments will be handled by [AttachmentManagerInterface].
 */
class MessageInterpreter(
    private val directiveGroupProcessor: DirectiveGroupProcessor,
    private val attachmentManager: AttachmentManagerInterface
) : MessageObserver {
    companion object {
        private const val TAG = "MessageInterpreter"
        private const val KEY_DIRECTIVES = "directives"
        private const val KEY_ATTACHMENT = "attachment"
    }

    private val gson = Gson()

    override fun receive(message: String) {
        // message의 parsing을 담당.
        try {
            val jsonObject = JsonParser().parse(message).asJsonObject
            when {
                jsonObject.has(KEY_DIRECTIVES) -> onReceiveDirectives(jsonObject)
                jsonObject.has(KEY_ATTACHMENT) -> onReceiveAttachment(jsonObject)
                else -> onReceiveUnknownMessage(message)
            }
        } catch (e: Exception) {
            onReceiveUnknownMessage(message)
        }
    }

    private fun onReceiveDirectives(jsonObject: JsonObject) {
        Logger.d(TAG, "[onReceiveDirectives] $jsonObject")
        val jsonArray = jsonObject.getAsJsonArray(KEY_DIRECTIVES)
        var directives = ArrayList<Directive>()

        for (jsonElement in jsonArray) {
            if (jsonElement.isJsonObject) {
                convertJsonToDirective(jsonElement.asJsonObject)?.let {
                    directives.add(it)
                }
            } else {
                Logger.w(TAG, "[onReceiveDirectives] directive is not json object: $jsonElement")
            }
        }

        directiveGroupProcessor.onReceiveDirectives(directives)
    }

    private fun convertJsonToDirective(jsonObject: JsonObject): Directive? {
        val directive = createDirective(attachmentManager, jsonObject)
        return if (directive != null) {
            directive
        } else {
            Logger.e(TAG, "[convertJsonToDirective] failed to create Directive from: $jsonObject")
            null
        }
    }

    private fun onReceiveAttachment(jsonObject: JsonObject) {
        val jsonAttachment = jsonObject.getAsJsonObject(KEY_ATTACHMENT)
        val attachment = createAttachmentMessage(jsonAttachment)
        if (attachment != null) {
            // change jsonAttachment to brief log
            try {
                jsonAttachment.remove("content")
                jsonAttachment.addProperty("content", "...")
            } catch (e: Exception) {
            }
            Logger.d(TAG, "[onReceiveAttachment] $jsonAttachment")
            attachmentManager.onAttachment(attachment)
        } else {
            Logger.e(TAG, "[onReceiveAttachment] failed to create Attachment from: $jsonObject")
        }
    }

    private fun onReceiveUnknownMessage(message: String) {
        Logger.e(TAG, "[onReceiveUnknownMessage] $message")
    }

    private fun createDirective(attachmentManager: AttachmentManagerInterface?, json: JsonObject): Directive? {
        return try {
            val header = createHeader(json.getAsJsonObject("header"))
            val payload = json.getAsJsonPrimitive("payload").asString
            Directive(
                attachmentManager,
                header,
                payload/*JsonParser().parse(payload).asJsonObject*/
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun createAttachmentMessage(json: JsonObject): AttachmentMessage? {
        return try {
            val content = createContent(json.getAsJsonObject("content"))
            val header = createHeader(json.getAsJsonObject("header"))
            val isEnd = json["isEnd"].asBoolean
            val parentMessageId = json["parentMessageId"].asString
            val seq = json["seq"].asInt

            AttachmentMessage(content, header, isEnd, parentMessageId, seq)
        } catch (e: Exception) {
            null
        }
    }

    private fun createHeader(jsonObject: JsonObject) = Header(
        jsonObject["dialogRequestId"].asString,
        jsonObject["messageId"].asString,
        jsonObject["name"].asString,
        jsonObject["namespace"].asString,
        jsonObject["version"].asString
    )

    private fun createContent(jsonObject: JsonObject): ByteArray = gson.fromJson(jsonObject["bytes"].asJsonArray, ByteArray::class.java)
}