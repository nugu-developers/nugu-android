package com.skt.nugu.sdk.platform.android.ux.template.webview

import com.google.gson.JsonParser
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.template.controller.NuguTemplateHandler

enum class ButtonEvent(val eventType: String) {
    ElementSelected("Display.ElementSelected") {
        override fun handle(templateHandler: NuguTemplateHandler, data: String) {
            runCatching { JsonParser.parseString(data).asJsonObject }
                .onSuccess {
                    val token = runCatching { it.get("token") }.getOrNull()?.asString
                    val postback = runCatching { it.get("postback") }.getOrNull()?.asString

                    if (token == null) {
                        Logger.d(TAG, "ElementSelected fail. token missing")
                    } else {
                        templateHandler.onElementSelected(token, if (postback?.isNotBlank() == true) postback else null)
                    }
                }
                .onFailure {
                    Logger.d(TAG, "ElementSelected fail. data missing  $data")
                }
        }
    },

    TextInput("Text.TextInput") {
        override fun handle(templateHandler: NuguTemplateHandler, data: String) {
            runCatching { JsonParser.parseString(data).asJsonObject }
                .onSuccess {
                    val text = runCatching { it.get("text") }.getOrNull()?.asString
                    val playServiceId = runCatching { it.get("playServiceId") }.getOrNull()?.asString

                    if (text == null) {
                        Logger.d(TAG, "TextInput fail. text missing")
                    } else {
                        templateHandler.getNuguClient().textAgent?.requestTextInput(text, playServiceId)
                    }
                }
                .onFailure {
                    Logger.d(TAG, "TextInput fail. data missing $data")
                }
        }
    },

    EVENT("EVENT") {
        override fun handle(templateHandler: NuguTemplateHandler, data: String) {
            runCatching { JsonParser.parseString(data).asJsonObject }
                .onSuccess {
                    val type = runCatching { it.get("type") }.getOrNull()?.asString
                    val playServiceId = runCatching { it.get("data").asJsonObject.get("playServiceId") }.getOrNull()?.asString
                    val eventData = runCatching { it.get("data").asJsonObject.get("data") }.getOrNull()

                    if (type == null || playServiceId == null || eventData == null) {
                        Logger.d(TAG, "EVENT fail. something missing. type : $type, playServiceId : $playServiceId, data : $eventData")
                    } else {
                        when (type) {
                            "Extension.CommandIssued" -> {
                                templateHandler.getNuguClient()?.extensionAgent?.issueCommand(
                                    playServiceId,
                                    eventData.asString,
                                    null)
                            }
                            "Display.TriggerChild" -> {
                                templateHandler.getNuguClient()?.displayAgent?.triggerChild(
                                    templateHandler.templateInfo.templateId,
                                    playServiceId,
                                    eventData.asJsonObject,
                                    null)
                            }
                            else -> {
                                Logger.d(TAG, "EVENT fail. UNKNOWN EVENT type : $type")
                            }
                        }
                    }
                }
                .onFailure {
                    Logger.d(TAG, "EVENT fail. data missing $data")
                }
        }
    },

    CONTROL("CONTROL") {
        override fun handle(templateHandler: NuguTemplateHandler, data: String) {
            runCatching { JsonParser.parseString(data).asJsonObject }
                .onSuccess {
                    val type = runCatching { it.get("type") }.getOrNull()?.asString

                    if (type == null) {
                        Logger.d(TAG, "CONTROL fail. type missing")
                    } else {
                        when (type) {
                            "TEMPLATE_PREVIOUS" -> {
                                templateHandler.onCloseClicked()
                            }
                            "TEMPLATE_CLOSEALL" -> {
                                templateHandler.onCloseWithParents()
                            }
                            else -> {
                                Logger.d(TAG, "CONTROL fial. UNKNOWN CONTROL type : $type")
                            }
                        }
                    }
                }
                .onFailure {
                    Logger.d(TAG, "CONTROL fail. data missing $data")
                }
        }
    };

    abstract fun handle(templateHandler: NuguTemplateHandler, data: String)

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
