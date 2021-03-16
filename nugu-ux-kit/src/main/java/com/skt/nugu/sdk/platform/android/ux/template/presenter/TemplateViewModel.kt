package com.skt.nugu.sdk.platform.android.ux.template.presenter

import androidx.lifecycle.ViewModel
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.platform.android.ux.template.controller.TemplateHandler


class TemplateViewModel : ViewModel() {
    companion object {
        const val TAG = "TemplateViewModel"
    }

    lateinit var nuguClientProvider: TemplateRenderer.NuguClientProvider
    lateinit var externalRenderer: TemplateRenderer.ExternalViewRenderer
    var renderNotified = TemplateFragment.RenderNotifyState.NONE
    var onClose: (() -> Unit)? = null
    var templateHandler: TemplateHandler? = null

    fun mergeTemplate(template: String, newTemplate: String): String {
        val savedTemplate = JsonParser.parseString(template).asJsonObject
        val updatedTemplate = JsonParser.parseString(newTemplate).asJsonObject.run {
            when (this.has("template")) {
                true -> this.get("template").asJsonObject
                false -> this
            }
        }

        return deepMerge(updatedTemplate, savedTemplate).toString().also {
            Logger.d(TAG, "merge check $it")
        }
    }

    private fun deepMerge(source: JsonObject, target: JsonObject): JsonObject? {
        for ((key, value) in source.entrySet()) {
            if (!target.has(key)) {
                //target does not have the same key, so perhaps it should be added to target
                if (!value.isJsonNull) //well, only add if the source value is not null
                    target.add(key, value)
            } else {
                if (!value.isJsonNull) {
                    if (value.isJsonObject) {
                        //source value is json object, start deep merge
                        deepMerge(value.asJsonObject, target[key].asJsonObject)
                    } else {
                        target.add(key, value)
                    }
                } else {
                    target.remove(key)
                }
            }
        }
        return target
    }

    override fun onCleared() {
        super.onCleared()
        onClose?.invoke()
        Logger.d(TAG, "cleared")
        templateHandler?.clear()

        onClose = null
        templateHandler = null
    }
}