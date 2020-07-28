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
package com.skt.nugu.sdk.core.interfaces.message

import java.util.*
class Status(val code: Code) {
    var cause: Throwable? = null
    var description: String? = null
    val error: StatusError = fromCodeToError(code)

    companion object {
        private val STATUS_LIST: List<Status> = buildStatusList()

        fun fromCode(value: Int): Status {
            return if (value < 0 || value > STATUS_LIST.size) {
                Status(Code.UNKNOWN)
            } else {
                STATUS_LIST[value]
            }
        }

        private fun buildStatusList(): List<Status> {
            val canonicalizer: TreeMap<Int, Status> =
                TreeMap<Int, Status>()
            for (code in Code.values()) {
                val replaced: Status? = canonicalizer.put(code.value, Status(code))
                check(replaced == null) {
                    ("Code value duplication between "
                            + replaced?.code?.name + " & " + code.name)
                }
            }
            return Collections.unmodifiableList(
                ArrayList(
                    canonicalizer.values
                )
            )
        }

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
        val INVALID_ARGUMENT: Status = Code.INVALID_ARGUMENT.toStatus()
        val DEADLINE_EXCEEDED: Status = Code.DEADLINE_EXCEEDED.toStatus()
        val NOT_FOUND: Status = Code.NOT_FOUND.toStatus()
        val ALREADY_EXISTS: Status = Code.ALREADY_EXISTS.toStatus()
        val PERMISSION_DENIED: Status = Code.PERMISSION_DENIED.toStatus()
        val UNAUTHENTICATED: Status = Code.UNAUTHENTICATED.toStatus()
        val RESOURCE_EXHAUSTED: Status = Code.RESOURCE_EXHAUSTED.toStatus()
        val FAILED_PRECONDITION: Status = Code.FAILED_PRECONDITION.toStatus()
        val ABORTED: Status = Code.ABORTED.toStatus()
        val OUT_OF_RANGE: Status = Code.OUT_OF_RANGE.toStatus()
        val UNIMPLEMENTED: Status = Code.UNIMPLEMENTED.toStatus()
        val INTERNAL: Status = Code.INTERNAL.toStatus()
        val UNAVAILABLE: Status = Code.UNAVAILABLE.toStatus()
        val DATA_LOSS: Status = Code.DATA_LOSS.toStatus()

        private fun Code.toStatus(): Status {
            return Status(this)
        }
    }

    fun isOk() = Code.OK == code
    fun isTimeout() = Code.DEADLINE_EXCEEDED == code

    fun fromCodeToError(code: Code): StatusError {
        if(Code.OK == code) {
            return StatusError.OK
        } else if(Code.DEADLINE_EXCEEDED == code) {
            return StatusError.TIMEOUT
        } else if(Code.UNAUTHENTICATED == code) {
            return StatusError.UNAUTHENTICATED
        } else if(Code.FAILED_PRECONDITION == code) {
            return StatusError.UNKNOWN
        } else if(Code.CANCELLED == code) {
            return StatusError.OK
        }
        return StatusError.NETWORK
    }

    enum class StatusError {
        OK,
        TIMEOUT,
        UNAUTHENTICATED,
        NETWORK,
        UNKNOWN
    }

    enum class Code(val value: Int, val description: String, val httpCode: Int) {
        OK(0, "OK", 200),
        CANCELLED(1, "Client Closed Request", 499),
        UNKNOWN(2, "Internal Server Error", 500),
        INVALID_ARGUMENT(3, "Bad Request", 400),
        DEADLINE_EXCEEDED(4, "Gateway Timeout", 504),
        NOT_FOUND(5, "Not Found", 404),
        ALREADY_EXISTS(6, "Conflict", 409),
        PERMISSION_DENIED(7, "Forbidden", 403),
        RESOURCE_EXHAUSTED(8, "Too Many Requests", 429),
        FAILED_PRECONDITION(9, "Bad Request", 400),
        ABORTED(10, "Conflict", 409),
        OUT_OF_RANGE(11, "Bad Request", 400),
        UNIMPLEMENTED(12, "Not Implemented", 501),
        INTERNAL(13, "Internal Server Error", 500),
        UNAVAILABLE(14, " Service Unavailable", 503),
        DATA_LOSS(15, "Internal Server Error", 500),
        UNAUTHENTICATED(16, "Unauthorized", 401)
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Status : code=").append(code.name)
        sb.append(", description=").append(description)
        sb.append(", cause=").append(cause)
        sb.append(", error=").append(error.name)
        return sb.toString()
    }
}