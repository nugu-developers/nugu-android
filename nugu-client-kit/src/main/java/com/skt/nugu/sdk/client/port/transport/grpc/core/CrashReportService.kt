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
package com.skt.nugu.sdk.client.port.transport.grpc.core

import com.skt.nugu.sdk.client.port.transport.grpc.Channels
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.*
import io.grpc.Status
import io.grpc.stub.StreamObserver

internal class CrashReportService(
    val observer: GrpcServiceListener
) : GrpcServiceInterface {
    companion object {
        private const val TAG = "GrpcTransport"
    }

    private var channel: Channels? = null
    private var stub: VoiceServiceGrpc.VoiceServiceStub? = null

    override fun shutdown() {
        this.stub = null
    }

    override fun connect(channel: Channels) {
        this.channel = channel
        this.stub = VoiceServiceGrpc.newStub(channel.getChannel())
    }

    /**
     * The underlying channel of the stub.
     * @return the CrashReportService stub
     */
    private fun getStub(): VoiceServiceGrpc.VoiceServiceStub? {
        if (stub == null) {
            this.stub = VoiceServiceGrpc.newStub(channel?.getChannel())
        }
        return this.stub
    }

    fun sendCrashReport(level: Int, message: String) {
        val builder =
            CrashReportRequest.CrashReport.newBuilder()
                .setDetail(message)
                .setLevel(CrashReportRequest.CrashReport.Level.forNumber(level))
                .build()

        val request = CrashReportRequest.newBuilder()
            .addCrashReport(builder).build()

        getStub()?.sendCrashReport(request, object : StreamObserver<CrashReportResponse> {
            override fun onNext(value: CrashReportResponse?) {
                Logger.d(TAG, "[CrashReportService] onNext")
            }

            override fun onError(t: Throwable?) {
                val status = Status.fromThrowable(t)
                Logger.d(
                    TAG,
                    "[CrashReportService] error (code:{${status.code}}, desc:{${status.description}}"
                )
            }

            /// End of the response
            override fun onCompleted() {
                Logger.d(TAG, "[CrashReportService] onCompleted")
            }
        })
    }
}
