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
package com.skt.nugu.sdk.agent.asr.impl

import com.skt.nugu.sdk.agent.DefaultASRAgent
import com.skt.nugu.sdk.agent.asr.audio.AudioFormat
import com.skt.nugu.sdk.agent.asr.ASRAgentInterface
import com.skt.nugu.sdk.agent.asr.audio.Encoder
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.agent.sds.SharedDataStream
import com.skt.nugu.sdk.core.interfaces.message.request.AttachmentMessageRequest
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.utils.Logger
import com.skt.nugu.sdk.core.utils.UUIDGeneration

class SpeechRecognizeAttachmentSenderThread(
    private val reader: SharedDataStream.Reader,
    private val inputFormat: AudioFormat,
    private val messageSender: MessageSender,
    private val observer: RecognizeSenderObserver,
    private val audioEncoder: Encoder,
    private val recognizeEvent: EventMessageRequest
) : Thread() {
    companion object {
        private const val TAG = "SpeechRecognizeAttachmentSenderThread"
    }

    interface RecognizeSenderObserver {
        fun onFinish()
        fun onStop()
        fun onError(errorType: ASRAgentInterface.ErrorType)
    }

    @Volatile
    private var isStopping = false
    private var currentAttachmentSequenceNumber: Int = 0

    override fun run() {
        try {
            Logger.d(TAG, "[run] start")
            if (!audioEncoder.startEncoding(inputFormat)) {
                observer.onError(ASRAgentInterface.ErrorType.ERROR_AUDIO_INPUT)
                return
            }

            val buffer = ByteArray(140 * inputFormat.getBytesPerMillis())
            var encodedBuffer: ByteArray?
            var read: Int

            while (true) {
                if (isStopping) {
                    Logger.d(TAG, "[run] stop: isStopping is true")
                    break
                }

                if (reader.isClosed()) {
                    Logger.d(TAG, "[run] stop: reader closed")
                    break
                }
                // 1. read data
                read = reader.read(buffer, 0, buffer.size)

                // 2. encode data
                encodedBuffer = if (read > 0) {
                    audioEncoder.encode(buffer, 0, read)
                } else {
                    null
                }

                // 3. send data
                if (encodedBuffer != null) {
                    if (!sendAttachment(encodedBuffer, recognizeEvent)) {
                        observer.onError(ASRAgentInterface.ErrorType.ERROR_NETWORK)
                        return
                    }
                }
            }

            if (isStopping) {
                observer.onStop()
            } else {
                if (!sendAttachment(null, recognizeEvent)) {
                    observer.onError(ASRAgentInterface.ErrorType.ERROR_NETWORK)
                } else {
                    observer.onFinish()
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "[exception]", e)
            observer.onError(ASRAgentInterface.ErrorType.ERROR_UNKNOWN)
        } finally {
            Logger.d(TAG, "[run] end")
            audioEncoder.stopEncoding()
            reader.close()
        }
    }

    private fun sendAttachment(encoded: ByteArray?, request: EventMessageRequest): Boolean {
        Logger.d(
            TAG,
            "[sendAttachment] $currentAttachmentSequenceNumber, ${encoded == null}, $this"
        )

        val attachmentMessage =
            AttachmentMessageRequest(
                UUIDGeneration.timeUUID().toString(),
                request.dialogRequestId,
                request.context,
                DefaultASRAgent.NAMESPACE,
                DefaultASRAgent.NAME_RECOGNIZE,
                DefaultASRAgent.VERSION,
                request.dialogRequestId,
                currentAttachmentSequenceNumber,
                encoded == null,
                request.messageId,
                "audio/speex", // TODO: Get info from Encoder
                encoded
            )

        currentAttachmentSequenceNumber++
        return messageSender.sendMessage(attachmentMessage)
    }

    fun requestStop() {
        Logger.d(TAG, "[requestStop] $this")
        isStopping = true
        reader.close()
    }

    fun requestFinish() {
        if (isStopping) {
            Logger.d(TAG, "[requestFinish] skip: ($this) is Stopping")
            return
        }

        Logger.d(TAG, "[requestFinish] $this")
        reader.close()
    }
}