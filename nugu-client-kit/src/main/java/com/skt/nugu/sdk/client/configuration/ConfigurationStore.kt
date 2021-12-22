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
package com.skt.nugu.sdk.client.configuration

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.jvm.Throws

/**
 * This is a helper for configuration. It contains the APIs for getting the configuration
 **/
object ConfigurationStore {
    const val TAG = "ConfigurationStore"
    @Volatile
    private var isInitializing = false
    private val executor = Executors.newSingleThreadExecutor()
    private var serviceConfigurationMetadata : ConfigurationMetadata? = null
    lateinit var configuration : Configuration
    private val listeners =  Collections.synchronizedSet(mutableSetOf<ConfigurationCallback>())
    private fun discoveryUrl() =
        "${configuration.OAuthServerUrl}/.well-known/oauth-authorization-server"

    /**
     * Callback for [ConfigurationMetadata] results.
     */
    interface ConfigurationCallback {
        fun onConfigurationCompleted(metadata: ConfigurationMetadata)
        fun onConfigurationFailed(error: Throwable)
    }
    /**
     * Set the [Configuration]
     * start AuthorizationServiceConfiguration
     */
    fun configure(configuration: Configuration) {
        this.configuration = configuration
        obtainAuthorizationServiceConfiguration()
    }
    // InputStream to json
    fun configure(inputStream: InputStream) {
        readInputStream(inputStream).apply {
            configure(this)
        }
    }
    // json to Configuration
    fun configure(json: String) {
        parseConfiguration(json)?.apply {
            configure(this)
        } ?: throw IOException("[configure] Failed parsing JSON source: $json to Json")
    }
    // InputStream to string
    private fun readInputStream(inputStream: InputStream) : String {
        val stringBuilder = StringBuilder()
        BufferedReader(InputStreamReader(inputStream)).forEachLine {
            stringBuilder.append(it)
        }
        inputStream.close()
        return stringBuilder.toString()
    }
    // Parse the string.
    private fun parseConfiguration(json: String) : Configuration? {
        return try {
            Gson().fromJson(JsonParser.parseString(json).asJsonObject, Configuration::class.java)
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Gets the value of [ConfigurationMetadata.policyUri]
     * @param onResult is called after the value is computed
     * @error Throwable on get errors.
     */
    fun privacyUrl(onResult:(url : String, error: Throwable?) -> Unit) {
        obtainAuthorizationServiceConfiguration(object : ConfigurationCallback {
            override fun onConfigurationCompleted(metadata: ConfigurationMetadata) {
                onResult(metadata.policyUri.toString(), null)
            }

            override fun onConfigurationFailed(error: Throwable) {
                onResult("", error)
            }
        })
    }
    /**
     * Gets the value of [ConfigurationMetadata.templateServerUri]
     * @param onResult is called after the value is computed
     * @error Throwable on get errors.
     */
    fun templateServerUri(onResult:(url : String, error: Throwable?) -> Unit) {
        obtainAuthorizationServiceConfiguration(object : ConfigurationCallback {
            override fun onConfigurationCompleted(metadata: ConfigurationMetadata) {
                onResult(metadata.templateServerUri.toString(), null)
            }

            override fun onConfigurationFailed(error: Throwable) {
                onResult("", error)
            }
        })
    }
    /**
     * Gets the value of usageGuideUrl
     * @param onResult is called after the value is computed
     * @error Throwable on get errors.
     */
    fun usageGuideUrl(deviceUniqueId: String, onResult:(String, Throwable?) -> Unit) {
        obtainAuthorizationServiceConfiguration(object : ConfigurationCallback {
            override fun onConfigurationCompleted(metadata: ConfigurationMetadata) {
                val url = StringBuilder().apply {
                    append(metadata.serviceDocumentation)
                    append("?")
                    append("poc_id=${configuration.pocId}")
                    append("&")
                    append("device_unique_id=${deviceUniqueId}")
                }
                onResult(url.toString(), null)
            }

            override fun onConfigurationFailed(error: Throwable) {
                onResult("", error)
            }
        })
    }
    /**
     * Gets the value of [ConfigurationMetadata.serviceSetting]
     * @param onResult is called after the value is computed
     * @error Throwable on get errors.
     */
    fun serviceSettingUrl(onResult:(url : String, error: Throwable?) -> Unit) {
        obtainAuthorizationServiceConfiguration(object : ConfigurationCallback {
            override fun onConfigurationCompleted(metadata: ConfigurationMetadata) {
                onResult(metadata.serviceSetting.toString(), null)
            }

            override fun onConfigurationFailed(error: Throwable) {
                onResult("", error)
            }
        })
    }
    /**
     * Gets the value of [ConfigurationMetadata.termOfServiceUri]
     * @param onResult is called after the value is computed
     * @error Throwable on get errors.
     */
    fun agreementUrl(onResult:(String, Throwable?) -> Unit) {
        obtainAuthorizationServiceConfiguration(object : ConfigurationCallback {
            override fun onConfigurationCompleted(metadata: ConfigurationMetadata) {
                onResult(metadata.termOfServiceUri, null)
            }

            override fun onConfigurationFailed(error: Throwable) {
                onResult("", error)
            }
        })
    }

    /**
     * Returns the [Configuration]
     */
    fun configuration() = try {
        configuration
    } catch (e: UninitializedPropertyAccessException) {
        null
    }

    /**
     * Returns the [ConfigurationMetadata] by synchronously, Fetching from cache or network
     */
    fun configurationMetadataSync() : ConfigurationMetadata? {
        var result : ConfigurationMetadata? = null
        val latch = CountDownLatch(1)
        obtainAuthorizationServiceConfiguration(object : ConfigurationCallback {
            override fun onConfigurationCompleted(metadata: ConfigurationMetadata) {
                result = metadata
                latch.countDown()
            }

            override fun onConfigurationFailed(error: Throwable) {
                latch.countDown()
            }
        })
        latch.await()
        return result
    }

    /**
     * Set the value of [ConfigurationMetadata]
     */
    fun configurationMetadata(configurationMetadata: ConfigurationMetadata?) {
        synchronized(this) {
            serviceConfigurationMetadata = configurationMetadata
        }
    }

    /**
     * Get the value of [ConfigurationMetadata]
     */
    fun configurationMetadata() = synchronized(this) {
        serviceConfigurationMetadata
    }

    /**
     * Clears the [ConfigurationMetadata]
     */
    fun clearConfigurationMetadata() {
        configurationMetadata(null)
    }

    /**
     * Returns the [ConfigurationMetadata] by asynchronous, Fetching from cache or network
     */
    fun configurationMetadataAsync(onResult:(ConfigurationMetadata?, Throwable?) -> Unit) {
        obtainAuthorizationServiceConfiguration(object : ConfigurationCallback {
            override fun onConfigurationCompleted(metadata: ConfigurationMetadata) {
                onResult(metadata, null)
            }

            override fun onConfigurationFailed(error: Throwable) {
                onResult(null, error)
            }
        })
    }

    /**
     * Obtaining authorization Service Configuration. Fetching from cache or network
     */
    private fun obtainAuthorizationServiceConfiguration(callback: ConfigurationCallback? = null) {
        addObserver(callback)

        configurationMetadata()?.apply {
            notifySuccessListeners(this)
            removeObserver(callback)
            return
        }
        if(isInitializing) {
            return
        }
        isInitializing = true

        executor.submit {
            runCatching {
                requestDiscovery().apply {
                    configurationMetadata(this)
                }
            }.onSuccess {
                notifySuccessListeners(it)
            }.onFailure {
                notifyFailureListeners(it)
            }
            removeObserver(callback)
            isInitializing = false
        }
    }

    /**
     * Request the discovery api.
     * @return the [ConfigurationMetadata]
     * @throws JsonParseException – if the specified text is not valid JSON
     * @throws JsonSyntaxException – if json is not a valid representation for an object of type typeOfT
     * @throws IllegalStateException - If an unexpected error occurs
     */
    @Throws(JsonParseException::class, JsonSyntaxException::class, java.lang.IllegalStateException::class)
    private fun requestDiscovery(): ConfigurationMetadata {
        val client = OkHttpClient().newBuilder()
            .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
            .protocols(listOf(Protocol.HTTP_1_1)).build()
        val httpUrl  = try {
            discoveryUrl().toHttpUrl()
        } catch (th: Throwable) {
            throw IllegalStateException("An unexpected error has occurred (throwable=$th)")
        }

        val request = Request.Builder().url(httpUrl)
            .header("Accept", "application/json")
            .header("Authorization",
                "Basic " + "${configuration.clientId}:${configuration.clientSecret}".encodeBase64()
            )
            .build()
        val response = client.newCall(request).execute()
        val code = response.code
        when (code) {
            HttpURLConnection.HTTP_OK -> {
                val jsonObject = JsonParser.parseString(response.body?.string()).asJsonObject
                return Gson().fromJson(jsonObject, ConfigurationMetadata::class.java)
            }
        }
        throw IllegalStateException("An unexpected error has occurred (code=$code)")
    }

    /**
     * Adds a ConfigurationCallback
     */
    private fun addObserver(callback: ConfigurationCallback?) {
        if(callback == null) {
            return
        }
        listeners.add(callback)
    }

    /**
     * Removes the given observer from the observers list.
     */
    private fun removeObserver(callback: ConfigurationCallback?) {
        if(callback == null) {
            return
        }
        listeners.remove(callback)
    }

    private fun notifySuccessListeners(metadata: ConfigurationMetadata) {
        val listenersCopy: MutableList<ConfigurationCallback> = ArrayList(listeners)
        listenersCopy.forEach {
            it.onConfigurationCompleted(metadata)
        }
    }
    private fun notifyFailureListeners(e: Throwable) {
        val listenersCopy: MutableList<ConfigurationCallback> = ArrayList(listeners)
        listenersCopy.forEach {
            it.onConfigurationFailed(e)
        }
    }
}

internal fun String.encodeBase64(): String {
    val map: ByteArray = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toByteArray()
    val value = this.toByteArray()
    val size = value.size
    val length = (size + 2) / 3 * 4
    val out = ByteArray(length)
    var index = 0
    val end = size - size % 3
    var i = 0
    while (i < end) {
        val b0 = value[i++].toInt()
        val b1 = value[i++].toInt()
        val b2 = value[i++].toInt()
        out[index++] = map[(b0 and 0xff shr 2)]
        out[index++] = map[(b0 and 0x03 shl 4) or (b1 and 0xff shr 4)]
        out[index++] = map[(b1 and 0x0f shl 2) or (b2 and 0xff shr 6)]
        out[index++] = map[(b2 and 0x3f)]
    }
    when (size - end) {
        1 -> {
            val b0 = value[i].toInt()
            out[index++] = map[b0 and 0xff shr 2]
            out[index++] = map[b0 and 0x03 shl 4]
            out[index++] = '='.code.toByte()
            out[index] = '='.code.toByte()
        }
        2 -> {
            val b0 = value[i++].toInt()
            val b1 = value[i].toInt()
            out[index++] = map[(b0 and 0xff shr 2)]
            out[index++] = map[(b0 and 0x03 shl 4) or (b1 and 0xff shr 4)]
            out[index++] = map[(b1 and 0x0f shl 2)]
            out[index] = '='.code.toByte()
        }
    }
    return String(out, Charsets.UTF_8)
}