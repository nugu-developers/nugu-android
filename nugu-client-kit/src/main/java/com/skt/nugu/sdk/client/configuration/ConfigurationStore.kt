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
import com.google.gson.JsonParser
import com.squareup.okhttp.HttpUrl
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

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
    private val listeners = mutableSetOf<ConfigurationCallback>()
    private val discoveryUrl by lazy {
        "${configuration.OAuthServerUrl}/.well-known/oauth-authorization-server/${configuration.clientId}"
    }

    interface ConfigurationCallback {
        fun onConfigurationCompleted(metadata: ConfigurationMetadata)
        fun onConfigurationFailed(error: Throwable)
    }

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

    private fun readInputStream(inputStream: InputStream) : String {
        val stringBuilder = StringBuilder()
        BufferedReader(InputStreamReader(inputStream)).forEachLine {
            stringBuilder.append(it)
        }
        inputStream.close()
        return stringBuilder.toString()
    }

    private fun parseConfiguration(json: String) : Configuration? {
        return try {
            Gson().fromJson(JsonParser.parseString(json).asJsonObject, Configuration::class.java)
        } catch (e: Throwable) {
            null
        }
    }

   // * @param onResult is called after the value is computed
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

    fun configurationMetadata(onResult:(ConfigurationMetadata?, Throwable?) -> Unit) {
        obtainAuthorizationServiceConfiguration(object : ConfigurationCallback {
            override fun onConfigurationCompleted(metadata: ConfigurationMetadata) {
                onResult(metadata, null)
            }

            override fun onConfigurationFailed(error: Throwable) {
                onResult(null, error)
            }
        })
    }

    private fun obtainAuthorizationServiceConfiguration(callback: ConfigurationCallback? = null) {
        addObserver(callback)

        serviceConfigurationMetadata?.apply {
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
                    serviceConfigurationMetadata = this
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

    private fun requestDiscovery(): ConfigurationMetadata {
        val client = OkHttpClient().apply {
            protocols = listOf(com.squareup.okhttp.Protocol.HTTP_1_1)
        }
        val httpUrl = HttpUrl.parse(discoveryUrl)
        val request = Request.Builder().url(httpUrl)
            .header("Accept", "application/json")
            .build()
        val response = client.newCall(request).execute()
        val code = response.code()
        when (code) {
            HttpURLConnection.HTTP_OK -> {
                val jsonObject = JsonParser.parseString(response.body().string()).asJsonObject
                if (jsonObject.size() > 0) {
                    return Gson().fromJson(jsonObject, ConfigurationMetadata::class.java)
                }
            }
        }
        throw Throwable("An unexpected error has occurred (code=$code)")
    }

    private fun addObserver(callback: ConfigurationCallback?) {
        if(callback == null) {
            return
        }
        listeners.add(callback)
    }
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