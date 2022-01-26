package com.skt.nugu.sdk.platform.android.ux.template.presenter

import com.google.gson.JsonObject
import com.google.gson.JsonParser


fun mergeTemplate(template: String, newTemplate: String): String {
    return runCatching {
        val savedTemplate = JsonParser.parseString(template).asJsonObject
        val updatedTemplate = JsonParser.parseString(newTemplate).asJsonObject.run {
            when (this.has("template")) {
                true -> this.get("template").asJsonObject
                false -> this
            }
        }
        deepMerge(updatedTemplate, savedTemplate).toString()
    }.getOrNull().run {
        this ?: template
    }
}

fun deepMerge(source: JsonObject, target: JsonObject): JsonObject? {
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
                    if (value.isJsonArray && target.get(key).isJsonArray) {
                        val (sourceArray, targetArray) = Pair(value.asJsonArray, target.get(key).asJsonArray)
                        sourceArray.forEachIndexed { index, jsonElement ->
                            val emptyElement = jsonElement.toString() == "{}"
                                    || jsonElement.isJsonNull
                                    || jsonElement.asJsonObject.isJsonNull
                                    || jsonElement.toString().isEmpty()

                            if (!emptyElement) {
                                if (jsonElement.isJsonObject && targetArray[index].isJsonObject) {
                                    deepMerge(jsonElement.asJsonObject, targetArray[index].asJsonObject)
                                } else {
                                    targetArray[index] = jsonElement
                                }
                            }
                        }
                    } else {
                        target.add(key, value)
                    }
                }
            } else {
                target.remove(key)
            }
        }
    }
    return target
}
