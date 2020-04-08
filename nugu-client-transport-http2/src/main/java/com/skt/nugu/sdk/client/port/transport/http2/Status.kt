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
package com.skt.nugu.sdk.client.port.transport.http2

/**
 * A class that represents a Status of GRPC.
 */
class Status(val code: Code) {
    var cause: Throwable? = null
    var description: String? = null

    enum class Code(val value: Int) {
        OK(0),
        CANCELLED(1),
        UNKNOWN(2),
        INVALID_ARGUMENT(3),
        DEADLINE_EXCEEDED(4),
        NOT_FOUND(5),
        ALREADY_EXISTS(6),
        PERMISSION_DENIED(7),
        RESOURCE_EXHAUSTED(8),
        FAILED_PRECONDITION(9),
        ABORTED(10),
        OUT_OF_RANGE(11),
        UNIMPLEMENTED(12),
        INTERNAL(13),
        UNAVAILABLE(14),
        DATA_LOSS(15),
        UNAUTHENTICATED(16);
    }

    companion object {
        fun Status.withDescription(description: String?): Status {
            this.description = description
            return this
        }

        fun Status.withCause(cause: Throwable?): Status {
            this.cause = cause
            return this
        }

        fun fromThrowable(t: Throwable?): Status {
            return Status(
                Code.UNAVAILABLE
            ).withDescription(t?.message.toString()).withCause(t?.cause)
        }

        val OK: Status = Code.OK.toStatus()
        val CANCELLED: Status = Code.CANCELLED.toStatus()
        val UNKNOWN: Status = Code.UNKNOWN.toStatus()
        val INVALID_ARGUMENT: Status =
            Code.INVALID_ARGUMENT.toStatus()
        val DEADLINE_EXCEEDED: Status =
            Code.DEADLINE_EXCEEDED.toStatus()
        val NOT_FOUND: Status = Code.NOT_FOUND.toStatus()
        val ALREADY_EXISTS: Status =
            Code.ALREADY_EXISTS.toStatus()
        val PERMISSION_DENIED: Status =
            Code.PERMISSION_DENIED.toStatus()
        val UNAUTHENTICATED: Status =
            Code.UNAUTHENTICATED.toStatus()

        val RESOURCE_EXHAUSTED: Status =
            Code.RESOURCE_EXHAUSTED.toStatus()
        val FAILED_PRECONDITION: Status =
            Code.FAILED_PRECONDITION.toStatus()
        val ABORTED: Status = Code.ABORTED.toStatus()
        val OUT_OF_RANGE: Status =
            Code.OUT_OF_RANGE.toStatus()
        val UNIMPLEMENTED: Status =
            Code.UNIMPLEMENTED.toStatus()
        val INTERNAL: Status = Code.INTERNAL.toStatus()
        val UNAVAILABLE: Status = Code.UNAVAILABLE.toStatus()
        val DATA_LOSS: Status = Code.DATA_LOSS.toStatus()

        fun Code.toStatus(): Status {
            return Status(this)
        }
    }

}