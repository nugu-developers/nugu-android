package com.skt.nugu.sdk.platform.android.ux.template.webview

import com.google.gson.JsonParser
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.template.controller.BasicTemplateHandler
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler

enum class ButtonEvent(val eventType: String) {
    ElementSelected("Display.ElementSelected") {
        override fun handle(templateHandler: TemplateHandler, data: String) {
            runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull()?.run {
                val token = runCatching { get("token") }.getOrNull()?.asString
                val postback = runCatching { get("postback") }.getOrNull()?.asString

                if (token == null) {
                    Logger.d(TAG, "ElementSelected fail. token missing")
                } else {
                    templateHandler.onElementSelected(token, if (postback?.isNotBlank() == true) postback else null)
                }

                return
            }

            Logger.d(TAG, "TextInput fail. data missing  $data")
        }
    },

    TextInput("Text.TextInput") {
        override fun handle(templateHandler: TemplateHandler, data: String) {
            runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull()?.run {
                val text = runCatching { get("text") }.getOrNull()?.asString
                val playServiceId = runCatching { get("playServiceId") }.getOrNull()?.asString
                val textAgent = (templateHandler as? BasicTemplateHandler)?.getNuguClient()?.textAgent

                if (textAgent == null || text == null) {
                    Logger.d(TAG, "TextInput fail. something missing. textAgent : $textAgent, text : $text")
                } else {
                    textAgent.requestTextInput(text, playServiceId)
                }
                return
            }

            Logger.d(TAG, "TextInput fail. textInput missing $data")
        }
    },

    EVENT("EVENT") {
        override fun handle(templateHandler: TemplateHandler, data: String) {
            runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull()?.run {
                val type = runCatching { get("type") }.getOrNull()?.asString
                val playServiceId = runCatching { get("data").asJsonObject.get("playServiceId") }.getOrNull()?.asString
                val eventData = runCatching { get("data").asJsonObject.get("data") }.getOrNull()

                if (type == null || playServiceId == null || eventData == null) {
                    Logger.d(TAG, "EVENT fail. something missing. type : $type, playServiceId : $playServiceId, data : $eventData")
                    return
                }

                when (type) {
                    "Extension.CommandIssued" -> {
                        (templateHandler as? BasicTemplateHandler)?.getNuguClient()?.extensionAgent?.issueCommand(
                            playServiceId,
                            eventData.asString,
                            null)
                    }

                    "Display.TriggerChild" -> {
                        (templateHandler as? BasicTemplateHandler)?.getNuguClient()?.displayAgent?.triggerChild(
                            templateHandler.templateInfo.templateId,
                            playServiceId,
                            eventData.asJsonObject,
                            null)
                    }

                    else -> {
                        Logger.d(TAG, "UNKNOWN EVENT type : $type")
                    }
                }
            }

            Logger.d(TAG, "EVENT fail. event missing $data")
        }
    },

    CONTROL("CONTROL") {
        override fun handle(templateHandler: TemplateHandler, data: String) {
            runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull()?.run {
                val type = runCatching { get("type") }.getOrNull()?.asString

                if (type == null) {
                    Logger.d(TAG, "CONTROL fail. type missing. type : $type")
                    return
                }

                when (type) {
                    "TEMPLATE_PREVIOUS" -> {
                        templateHandler.onCloseClicked()
                    }
                    "TEMPLATE_CLOSEALL" -> {
                        templateHandler.onCloseWithParents()
                    }
                    else -> {
                        Logger.d(TAG, "UNKNOWN CONTROL type : $type")
                    }
                }
            }
        }
    };

    abstract fun handle(templateHandler: TemplateHandler, data: String)

    companion object {
        private const val TAG = "ButtonEvent"
        fun get(type: String): ButtonEvent? {
            values().forEach {
                if (it.eventType == type) return it
            }

            return null
        }
    }
}
