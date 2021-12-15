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

package com.skt.nugu.sdk.core.interfaces.dialog

/**
 * the storage interface for dialog attribute
 */
interface DialogAttributeStorageInterface {
    data class Attribute(
        val playServiceId: String?,
        val domainTypes: Array<String>?,
        val asrContext: AsrContext?
    ) {
        data class AsrContext(
            val task: String?,
            val sceneId: String?,
            val sceneText: Array<String>?,
            val playServiceId: String?
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as AsrContext

                if (task != other.task) return false
                if (sceneId != other.sceneId) return false
                if (sceneText != null) {
                    if (other.sceneText == null) return false
                    if (!sceneText.contentEquals(other.sceneText)) return false
                } else if (other.sceneText != null) return false
                if (playServiceId != other.playServiceId) return false

                return true
            }

            override fun hashCode(): Int {
                var result = task?.hashCode() ?: 0
                result = 31 * result + (sceneId?.hashCode() ?: 0)
                result = 31 * result + (sceneText?.contentHashCode() ?: 0)
                result = 31 * result + (playServiceId?.hashCode() ?: 0)
                return result
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Attribute

            if (playServiceId != other.playServiceId) return false
            if (!domainTypes.contentEquals(other.domainTypes)) return false
            if (asrContext != other.asrContext) return false

            return true
        }

        override fun hashCode(): Int {
            var result = playServiceId?.hashCode() ?: 0
            result = 31 * result + domainTypes.contentHashCode()
            result = 31 * result + asrContext.hashCode()
            return result
        }
    }
    fun setAttribute(key: String, attr : Attribute)
    fun getAttribute(key: String): Attribute?
    fun getRecentAttribute(): Attribute?
    fun removeAttribute(key: String)
}