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
package com.skt.nugu.sdk.core.interfaces.message

import java.nio.ByteBuffer

/**
 * data class for attachment message
 * @param content the binary data
 * @param header the header
 * @param isEnd the end flag of attachment
 * @param parentMessageId the parent message id which associated with attachment
 * @param seq the sequence number
 * @param mediaType the mime type for attachment
 */
data class AttachmentMessage(
    val content: ByteBuffer,
    val header: Header,
    val isEnd: Boolean,
    val parentMessageId: String,
    val seq: Int,
    val mediaType: String
)