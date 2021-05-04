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
package com.skt.nugu.sampleapp.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager


class PreferenceHelper {
    companion object {
        private val KEY_CREDENTIAL = "credential"
        private val KEY_ENABLE_NUGU = "enableNugu"
        private val KEY_ENABLE_TRIGGER = "enableTrigger"
        private val KEY_TRIGGER_KEYWORD = "triggerKeyword"
        private val KEY_ENABLE_WAKEUP_BEEP = "enableWakeupBeep"
        private val KEY_ENABLE_RECOGNITION_BEEP = "enableRecognitionBeep"
        private val KEY_ENABLE_RESPONSE_FAIL_BEEP = "enableResponseFailBeep"
        private val KEY_ENABLE_FLOATING = "enableFloating"
        private val KEY_DEVICE_UNIQUE_ID = "deviceUniqueId"

        private inline fun SharedPreferences.edit(task: (SharedPreferences.Editor) -> Unit) {
            val editor = this.edit()
            task(editor)
            editor.apply()
        }

        @Throws(UnsupportedOperationException::class)
        operator fun SharedPreferences.set(key: String, value: Any?) {
            when (value) {
                is String? -> edit { it.putString(key, value) }
                is Int -> edit { it.putInt(key, value) }
                is Float -> edit { it.putFloat(key, value) }
                is Long -> edit { it.putLong(key, value) }
                is Boolean -> edit { it.putBoolean(key, value) }
                else -> throw UnsupportedOperationException("No implementation")
            }
        }

        @Suppress("UNCHECKED_CAST")
        @Throws(UnsupportedOperationException::class)
        operator fun <T> SharedPreferences.get(key: String, defaultValue: T? = null): T {
            return when (defaultValue) {
                is String -> getString(key, defaultValue as? String) as T
                is Int -> getInt(key, defaultValue as? Int ?: -1) as T
                is Boolean -> getBoolean(key, defaultValue as? Boolean ?: false) as T
                is Float -> getFloat(key, defaultValue as? Float ?: -1f) as T
                is Long -> getLong(key, defaultValue as? Long ?: -1) as T
                else -> throw UnsupportedOperationException("No implementation")
            }
        }

        private operator fun invoke(context: Context): SharedPreferences  =
            PreferenceManager.getDefaultSharedPreferences(context)

        /***
         * Returns the credentials
         * @param context a context
         */
        fun credentials(context: Context): String {
            return this(context)[KEY_CREDENTIAL, ""]
        }

        /***
         * Set the credentials
         * @param context a context
         * @param value is JSON string
         */
        fun credentials(context: Context, value: String) {
            this(context)[KEY_CREDENTIAL] = value
        }

        /***
         * Returns the trigger keyword
         * @param context a context
         */
        fun triggerKeyword(context: Context, defValue: String): String {
            return this(context)[KEY_TRIGGER_KEYWORD, defValue]
        }

        /***
         * Returns the enabled status for NUGU
         * @param context a context
         * @return true is enable, otherwise false
         */
        fun enableNugu(context: Context): Boolean {
            return this(context)[KEY_ENABLE_NUGU, true]
        }

        /***
         * Set the enabled state of NUGU
         * @param context a context
         * @param value true is enable, otherwise false
         */
        fun enableNugu(context: Context, value: Boolean) {
            this(context)[KEY_ENABLE_NUGU] = value
        }

        /***
         * Returns call a name to start listening
         * @param context a context
         * @return true is enable, otherwise false
         */
        fun enableTrigger(context: Context): Boolean {
            return this(context)[KEY_ENABLE_TRIGGER, true]
        }

        /***
         * Sets call a name to start listening
         * @param context a context
         * @param value true is enable, otherwise false
         */
        fun enableTrigger(context: Context, value: Boolean) {
            this(context)[KEY_ENABLE_TRIGGER] = value
        }

        /***
         * Returns enable sound effect when start listening
         * @param context a context
         * @return true is enable, otherwise false
         */
        fun enableWakeupBeep(context: Context): Boolean {
            return this(context)[KEY_ENABLE_WAKEUP_BEEP, true]
        }

        /***
         * Sets enable sound effect when start listening
         * @param context a context
         * @param value true is enable, otherwise false
         */
        fun enableWakeupBeep(context: Context, value: Boolean) {
            this(context)[KEY_ENABLE_WAKEUP_BEEP] = value
        }

        /***
         * Returns enabled sound effect when Recognition success
         * @param context a context
         * @return true is enable, otherwise false
         */
        fun enableRecognitionBeep(context: Context): Boolean {
            return this(context)[KEY_ENABLE_RECOGNITION_BEEP, true]
        }

        /***
         * Sets enabled sound effect when Recognition success
         * @param context a context
         * @param value true is enable, otherwise false
         */
        fun enableRecognitionBeep(context: Context, value: Boolean) {
            this(context)[KEY_ENABLE_RECOGNITION_BEEP] = value
        }

        /***
         * Returns enabled sound effect when Response failed
         * @param context a context
         * @return true is enable, otherwise false
         */
        fun enableResponseFailBeep(context: Context): Boolean {
            return this(context)[KEY_ENABLE_RESPONSE_FAIL_BEEP, true]
        }

        /***
         * Sets enabled sound effect when Response failed
         * @param context a context
         * @param value true is enable, otherwise false
         */
        fun enableResponseFailBeep(context: Context, value: Boolean) {
            this(context)[KEY_ENABLE_RESPONSE_FAIL_BEEP] = value
        }

        /***
         * Returns enabled floating button when app is in background
         * @param context a context
         * @return true is enable, otherwise false
         */
        fun enableFloating(context: Context): Boolean {
            return this(context)[KEY_ENABLE_FLOATING, false]
        }

        /***
         * Sets enabled floating button when app is in background
         * @param context a context
         * @param value true is enable, otherwise false
         */
        fun enableFloating(context: Context, value: Boolean) {
            this(context)[KEY_ENABLE_FLOATING] = value
        }

        /***
         * Returns the device UniqueId
         * This is an internal function
         * @param context a context
         */
        fun deviceUniqueId(context: Context): String {
            return this(context)[KEY_DEVICE_UNIQUE_ID, ""]
        }

        /***
         * Set the device UniqueId
         * This is an internal function
         * @param context a context
         * @param value device UniqueId
         */
        fun deviceUniqueId(context: Context, value: String) {
            this(context)[KEY_DEVICE_UNIQUE_ID] = value
        }

    }
}
