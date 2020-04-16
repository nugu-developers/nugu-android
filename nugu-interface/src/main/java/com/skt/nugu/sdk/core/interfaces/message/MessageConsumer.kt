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

/**
 * An interface which allows a derived class to consume a Message from DeviceGateway.
 */
interface MessageConsumer {
    /** Called when directives has been received from DeviceGateway.
     * @param directives the received directives
     */
    fun consumeDirectives(directives: List<DirectiveMessage>)

    /** Called when an attachment has been received from DeviceGateway.
     * @param attachment the received attachment
     */
    fun consumeAttachment(attachment: AttachmentMessage)
}