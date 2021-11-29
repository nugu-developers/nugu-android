package com.skt.nugu.sdk.agent.text

import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.ConcurrentHashMap

internal class TextAttributeStorage {
    companion object {
        private const val TAG = "TextAttributeStorage"
    }

    private val attrs: MutableMap<String, Map<String, Any>> = ConcurrentHashMap()

    /**
     * @key the dialogRequestId
     */
    fun setAttributes(key: String, attr: Map<String, Any>) {
        Logger.d(TAG, "[setAttributes] key: $key, attr: $attr")
        attrs[key] = attr
    }

    /**
     * @key the dialogRequestId
     */
    fun getAttributes(key: String): Map<String, Any>? = attrs[key]

    /**
     * @key the dialogRequestId
     */
    fun removeAttributes(key: String) {
        attrs.remove(key).let {
            Logger.d(TAG, "[removeAttributes] key: $key, attr: $it")
        }
    }
}