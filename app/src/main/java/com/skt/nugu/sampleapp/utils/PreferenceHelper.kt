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

class PreferenceHelper {
    companion object {
        private val DEFAULT_PREF_NAME = "OPENSDK"

        private val KEY_CREDENTIAL = "credential"
        private val KEY_ENABLE_NUGU = "enableNugu"
        private val KEY_ENABLE_TRIGGER = "enableTrigger"
        private val KEY_TRIGGER_ID = "triggerId"
        private val KEY_ENABLE_WAKEUP_BEEP = "enableWakeupBeep"
        private val KEY_ENABLE_RECOGNITION_BEEP = "enableRecognitionBeep"
        private val KEY_AUTH_ID = "authId"
        private val KEY_DEVICE_UNIQUE_ID = "deviceUniqueId"

        private inline fun SharedPreferences.edit(task: (SharedPreferences.Editor) -> Unit) {
            val editor = this.edit()
            task(editor)
            editor.apply()
        }

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
            context.getSharedPreferences(DEFAULT_PREF_NAME, Context.MODE_PRIVATE)

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
         * Returns the trigger id
         * @param context a context
         */
        fun triggerId(context: Context): Int {
            return this(context)[KEY_TRIGGER_ID, 0]
        }

        /***
         * Set the trigger id to wakeup word.
         * @param context a context
         * @param value trigger id
         */
        fun triggerId(context: Context, value: Int) {
            this(context)[KEY_TRIGGER_ID] = value
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
            return this(context)[KEY_ENABLE_WAKEUP_BEEP, false]
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
            return this(context)[KEY_ENABLE_RECOGNITION_BEEP, false]
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
         * Returns the auth index
         * This is an internal function
         * @param context a context
         */
        fun authId(context: Context): Int {
            return this(context)[KEY_AUTH_ID, 0]
        }

        /***
         * Set the auth id
         * This is an internal function
         * @param context a context
         * @param value auth index
         */
        fun authId(context: Context, value: Int) {
            this(context)[KEY_AUTH_ID] = value
        }
        /***
         * Returns the device UniqueId
         * This is an internal function
         * @param context a context
         */
        fun deviceUniqueId(context: Context) : String {
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
