package com.skt.nugu.sdk.platform.android.login.extention

import org.json.JSONObject

/**
 * Appends a map of param to the json.
 */
internal fun Map<String, String>.appendDataToJson(newData : MutableMap<String, String>) : JSONObject {
    for ((key, value) in this) {
        newData[key] = value
    }
    return JSONObject(newData.toMap())
}