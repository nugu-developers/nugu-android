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
package com.skt.nugu.sampleapp.application

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.skt.nugu.sampleapp.client.ClientManager
import com.skt.nugu.sampleapp.service.SampleNotificationChannel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SampleApplication : Application() {
    companion object {
        private const val TAG = "SampleApplication"
    }

    override fun onCreate() {
        super.onCreate()
        initializeClientManager()
        createNotificationChannel()

        startKoin {
            androidContext(this@SampleApplication)
        }
    }

    /**
     * Init ClientManager
     */
    private fun initializeClientManager() {
        Log.d(TAG, "Init ClientManager")
        ClientManager.init(this)
    }

    /**
     * Registers notification channels
     * Need to add FOREGROUND_SERVICE permission in manifest.
     */
    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.d(TAG, "[createNotificationChannel] not create notification  channel(not supported)")
            return
        }
        Log.d(TAG, "[createNotificationChannel]")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        SampleNotificationChannel.values().forEach {
            notificationManager.createNotificationChannel(it.createChannel())
        }
    }
}