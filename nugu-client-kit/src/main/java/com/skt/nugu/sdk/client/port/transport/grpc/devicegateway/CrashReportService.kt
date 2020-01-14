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
package com.skt.nugu.sdk.client.port.transport.grpc.devicegateway

import com.skt.nugu.sdk.core.interfaces.message.request.CrashReportMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.*
import io.grpc.Status
import java.util.concurrent.TimeUnit
/**
 * This class is designed to manage crashreport of DeviceGateway
 */
internal class CrashReportService(val blockingStub: VoiceServiceGrpc.VoiceServiceBlockingStub) {
    companion object {
        private const val TAG = "CrashReportService"
        private const val defaultTimeout: Long = 1000 * 10L
    }

    fun sendCrashReport(crashReportMessageRequest : CrashReportMessageRequest) : Boolean {
        val builder =
            CrashReportRequest.CrashReport.newBuilder()
                .setDetail(crashReportMessageRequest.message)
                .setLevel(CrashReportRequest.CrashReport.Level.forNumber(crashReportMessageRequest.level.value))
                .build()

        val request = CrashReportRequest.newBuilder()
            .addCrashReport(builder).build()

        try {
            val response = blockingStub.withDeadlineAfter(
                defaultTimeout,
                TimeUnit.MILLISECONDS
            ).sendCrashReport(request)
        } catch (th: Throwable) {
            val status = Status.fromThrowable(th)
            Logger.d(TAG, "[onError] ${status.code}, ${status.description}")
            return false
        }
        return true
    }

    fun shutdown() {
        // nothing to do
    }
}
