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
package com.skt.nugu.sdk.platform.android.login.net

import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuthClient
import java.io.*
import java.net.URL
import javax.net.ssl.*

/**
 * Provide a base class for http client
 */
class HttpClient(private val delegate: NuguOAuthClient.UrlDelegate) {
    /**
     * Returns a [HttpsURLConnection] instance
     */
    @VisibleForTesting
    internal fun getConnection(uri: String, method: String, headers: Headers? = null) : HttpsURLConnection{
        val connection = URL(uri).openConnection() as HttpsURLConnection
        connection.hostnameVerifier = HostnameVerifier { _, session ->
            HttpsURLConnection.getDefaultHostnameVerifier().run {
                verify(Uri.parse(delegate.baseUrl()).host, session)
            }
        }
        connection.requestMethod = method
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.readTimeout = 10 * 1000
        connection.connectTimeout = 10 * 1000
        connection.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded")
        connection.setRequestProperty( "charset", "utf-8")
        connection.setRequestProperty("Accept", "application/json")
        headers?.let {
            for (i in 0 until it.size()) {
                connection.setRequestProperty( headers.name(i),  headers.value(i))

            }
        }
        return connection
    }

    /**
     * Prepare the request, Invokes the request immediately
     *
     * @throws IOException if the request could not be executed due to
     *     cancellation, a connectivity problem or timeout. Because networks can
     *     fail during an exchange, it is possible that the remote server
     *     accepted the request before the failure.
     */
    fun newCall(request: Request): Response {
        val connection = getConnection(request.uri, request.method, request.headers)
        return newCall(connection, request.form)
    }


    private fun newCall(connection: HttpsURLConnection, form: FormEncodingBuilder): Response {
        try {
            if(connection.requestMethod == "POST") {
                DataOutputStream(connection.outputStream).apply {
                    writeBytes(form.toString())
                    flush()
                    close()
                }
            }

            return when(connection.responseCode) {
                HttpsURLConnection.HTTP_OK -> {
                    val stream = BufferedInputStream(connection.inputStream)
                    Response(connection.responseCode, readStream(inputStream = stream))
                }
                else -> {
                    try {
                        val stream = BufferedInputStream(connection.errorStream)
                        Response(connection.responseCode, readStream(inputStream = stream))
                    } catch (e : FileNotFoundException) {
                        Response(connection.responseCode, e.message ?: "")
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    @VisibleForTesting
    internal fun readStream(inputStream: BufferedInputStream): String {
        val stringBuilder = StringBuilder()
        BufferedReader(InputStreamReader(inputStream)).forEachLine {
            stringBuilder.append(it)
        }
        return stringBuilder.toString()
    }
}
