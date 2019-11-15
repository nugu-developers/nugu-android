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
package com.skt.nugu.sampleapp.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.*
import com.skt.nugu.sdk.platform.android.login.auth.NuguOAuth
import com.skt.nugu.sampleapp.R
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.utils.PreferenceHelper


class SettingsActivity : AppCompatActivity() {
    companion object {
        fun invokeActivity(context: Context) {
            context.startActivity(Intent(context, SettingsActivity::class.java))
        }
    }

    private val switchEnableNugu: Switch by lazy {
        findViewById<Switch>(R.id.switch_enable_nugu)
    }

    private val switchEnableTrigger: Switch by lazy {
        findViewById<Switch>(R.id.switch_enable_trigger)
    }

    private val spinnerWakeupWord: Spinner by lazy {
        findViewById<Spinner>(R.id.spinner_wakeup_word)
    }

    private val switchEnableWakeupBeep: Switch by lazy {
        findViewById<Switch>(R.id.switch_enable_wakeup_beep)
    }

    private val switchEnableRecognitionBeep :Switch by lazy {
        findViewById<Switch>(R.id.switch_enable_recognition_beep)
    }

    private val buttonLogout : Button by lazy {
        findViewById<Button>(R.id.btn_logout)
    }

    private val spinnerAuthType: Spinner by lazy {
        findViewById<Spinner>(R.id.spinner_auth_type)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        switchEnableNugu.isChecked = PreferenceHelper.enableNugu(this)
        switchEnableTrigger.isChecked = PreferenceHelper.enableTrigger(this)
        switchEnableWakeupBeep.isChecked = PreferenceHelper.enableWakeupBeep(this)
        switchEnableRecognitionBeep.isChecked = PreferenceHelper.enableRecognitionBeep(this)

        spinnerWakeupWord.adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            add("아리아")
            add("팅커벨")
        }

        if (PreferenceHelper.triggerId(this) == 0) {
            spinnerWakeupWord.setSelection(0)
        } else {
            spinnerWakeupWord.setSelection(1)
        }
        initBtnListeners()
    }

    fun initBtnListeners() {
        switchEnableNugu.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.enableNugu(this,isChecked)
        }

        switchEnableTrigger.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.enableTrigger(this,isChecked)
        }

        spinnerWakeupWord.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (id == 0L) {
                    PreferenceHelper.triggerId(this@SettingsActivity,0)
                } else {
                    PreferenceHelper.triggerId(this@SettingsActivity,4)
                }
            }
        }

        switchEnableWakeupBeep.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.enableWakeupBeep(this, isChecked)
        }

        switchEnableRecognitionBeep.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.enableRecognitionBeep(this, isChecked)
        }

        buttonLogout.setOnClickListener {
            NuguOAuth.getClient().logout()
            ClientManager.getClient().shutdown()
            PreferenceHelper.credentials(this@SettingsActivity,"")
            finishAffinity()
        }
    }
}