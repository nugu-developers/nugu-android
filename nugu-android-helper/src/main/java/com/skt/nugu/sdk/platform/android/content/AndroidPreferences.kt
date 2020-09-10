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
package com.skt.nugu.sdk.platform.android.content

import android.content.Context
import android.content.SharedPreferences
import com.skt.nugu.sdk.core.interfaces.preferences.PreferencesInterface

/**
 * Default Implementation of [PreferencesInterface] for android
 */
class AndroidPreferences(val context: Context) :
    PreferencesInterface {
    companion object {
        private val DEFAULT_PREF_NAME = "NUGUSDK"
    }

    private fun SharedPreferences.edit(task: (SharedPreferences.Editor) -> Unit) {
        val editor = this.edit()
        task(editor)
        editor.apply()
    }

    private fun SharedPreferences.set(key: String, value: Any?) {
        when (value) {
            is String? -> edit { it.putString(key, value) }
            is Int -> edit { it.putInt(key, value) }
            is Float -> edit { it.putFloat(key, value) }
            is Long -> edit { it.putLong(key, value) }
            is Boolean -> edit { it.putBoolean(key, value) }
            else -> throw UnsupportedOperationException("No implementation")
        }
    }

    private fun <T> SharedPreferences.get(key: String, defaultValue: T? = null): T {
        return when (defaultValue) {
            is String -> getString(key, defaultValue as? String) as T
            is Int -> getInt(key, defaultValue as? Int ?: -1) as T
            is Boolean -> getBoolean(key, defaultValue as? Boolean ?: false) as T
            is Float -> getFloat(key, defaultValue as? Float ?: -1f) as T
            is Long -> getLong(key, defaultValue as? Long ?: -1) as T
            else -> throw UnsupportedOperationException("No implementation")
        }
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(DEFAULT_PREF_NAME, Context.MODE_PRIVATE)
    }

    override fun set(key: String, value: String) {
        sharedPreferences.set(key, value)
    }

    override fun get(key: String): String {
        return sharedPreferences.get(key, "")
    }
}