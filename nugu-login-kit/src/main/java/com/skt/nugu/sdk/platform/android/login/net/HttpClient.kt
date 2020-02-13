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
import java.io.*
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.*
import java.security.SecureRandom
import java.security.cert.X509Certificate

/**
 * Provide a base class for http client
 */
class HttpClient(private val baseUrl: String) {
    /**
     * Returns a [HttpsURLConnection] instance
     */
    private fun getConnection(uri: String) : HttpsURLConnection{
        val connection = URL(uri).openConnection() as HttpsURLConnection
        connection.hostnameVerifier = HostnameVerifier { _, session ->
            HttpsURLConnection.getDefaultHostnameVerifier().run {
                verify(Uri.parse(baseUrl).host, session)
            }
        }
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded")
        connection.setRequestProperty( "charset", "utf-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.useCaches = false
        connection.readTimeout = 10 * 1000
        connection.connectTimeout = 10 * 1000
        connection.doOutput = true
        connection.doInput = true
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
    fun newCall(uri: String, form: FormEncodingBuilder): Response {
        val connection = getConnection(uri)
        try {
            connection.connect()

            DataOutputStream(connection.outputStream).apply {
                writeBytes(form.toString())
                flush()
                close()
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

    private fun readStream(inputStream: BufferedInputStream): String {
        val stringBuilder = StringBuilder()
        BufferedReader(InputStreamReader(inputStream)).forEachLine {
            stringBuilder.append(it)
        }
        return stringBuilder.toString()
    }
}
