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
package com.skt.nugu.sdk.core.focus

import com.skt.nugu.sdk.core.interfaces.focus.ChannelObserver
import com.skt.nugu.sdk.core.interfaces.focus.FocusManagerInterface
import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import com.skt.nugu.sdk.core.utils.ImmediateBooleanFuture
import com.skt.nugu.sdk.core.utils.Logger
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.Future

class FocusManager(
    private val channelConfigurations: List<FocusManagerInterface.ChannelConfiguration>,
    tagHint: String? = null
    ) : FocusManagerInterface {

    private val TAG = if (tagHint.isNullOrBlank()) {
        "FocusManager"
    } else {
        "FocusManager_$tagHint"
    }

    private val allChannels: MutableMap<String, Channel> = HashMap()

    private var externalFocusInteractor: FocusManagerInterface.ExternalFocusInteractor? = null

    private data class ActiveChannel(
        val channel: Channel,
        val activatedIndex: Long = currentIndex++
    ) {
        companion object {
            private var currentIndex = 0L
        }
    }

    /**
     * activeChannels must be thread safe.
     * Do synchronize when use.
     */
    //@GuardedBy("activeChannels")
    private val activeChannels = TreeSet<ActiveChannel>(Comparator<ActiveChannel> { p0, p1 ->
        val diff = p1.channel.priority.acquire - p0.channel.priority.acquire
        if(diff != 0) {
            diff
        } else {
            p0.activatedIndex.compareTo(p1.activatedIndex)
        }
    })
    private val executor = Executors.newSingleThreadExecutor()
    private var foregroundChannel: Channel? = null

    private val listeners = CopyOnWriteArraySet<FocusManagerInterface.OnFocusChangedListener>()

    init {
        for ( (name, acquirePriority, releasePriority) in channelConfigurations){
            allChannels[name] = Channel(name, Channel.Priority(acquirePriority, releasePriority))
        }
    }

    override fun acquireChannel(
        channelName: String,
        channelObserver: ChannelObserver,
        interfaceName: String,
        finishListener: FocusManagerInterface.OnFinishListener?
    ): Boolean {
        val channelToAcquire = getChannel(channelName)

        if (channelToAcquire == null) {
            finishListener?.onFinish()
            return false
        }

        executor.submit {
            acquireChannelHelper(channelToAcquire, channelObserver, interfaceName, finishListener)
        }

        return true
    }

    override fun releaseChannel(channelName: String, channelObserver: ChannelObserver): Future<Boolean> {
        return getChannel(channelName).let { releaseTarget ->
            Logger.d(TAG, "[releaseChannel] $channelName, releaseTarget $releaseTarget")

            if (releaseTarget == null) {
                ImmediateBooleanFuture(false)
            } else {
                executor.submit(Callable<Boolean> {
                    releaseChannelHelper(releaseTarget, channelObserver)
                })
            }
        }
    }

    private fun acquireChannelHelper(
        channelToAcquire: Channel,
        channelObserver: ChannelObserver,
        interfaceName: String,
        finishListener: FocusManagerInterface.OnFinishListener?
    ) {
        // TODO: this should be called at last, but call first to solve issue (hack) should be fixed later.
        finishListener?.onFinish()

        val contains = synchronized(activeChannels) {
            activeChannels.filter {
                it.channel == channelToAcquire
            }.any()
        }

        val shouldReleaseChannelFocus =
            !(contains && channelToAcquire.getInterfaceName() == interfaceName)

        if (shouldReleaseChannelFocus) {
            setChannelFocus(channelToAcquire, FocusState.NONE)
            removeActiveChannel(channelToAcquire)
        }

        Logger.d(
            TAG,
            "[acquireChannelHelper] $channelToAcquire, $interfaceName, foreground: ${foregroundChannel?.name}"
        )

        channelToAcquire.setInterfaceName(interfaceName)
        synchronized(activeChannels) {
            val exist = activeChannels.filter {
                it.channel == channelToAcquire
            }.any()

            if(!exist) {
                ActiveChannel(channelToAcquire).let {
                    if (activeChannels.add(it)) {
                        Logger.d(TAG, "[acquireChannelHelper] create & added at active: $it")
                    }
                }
            } else {
                Logger.d(TAG, "[acquireChannelHelper] already active channel: $channelToAcquire")
            }
        }

        channelToAcquire.setObserver(channelObserver)

        val currentForegroundChannel = foregroundChannel

        when {
            currentForegroundChannel == null -> setChannelFocus(channelToAcquire, FocusState.FOREGROUND)
            currentForegroundChannel == channelToAcquire -> setChannelFocus(channelToAcquire, FocusState.FOREGROUND)
            channelToAcquire.priority.acquire <= currentForegroundChannel.priority.release -> {
                val higherPriorityChannelExceptForegroundChannel = synchronized(activeChannels) {
                    activeChannels.filter { it.channel.priority.release < channelToAcquire.priority.acquire && it.channel != currentForegroundChannel && it.channel != channelToAcquire }
                }
                Logger.d(TAG, "$activeChannels")

                if(higherPriorityChannelExceptForegroundChannel.any()) {
                    // Even if the request channel has higher priority than foreground channel, get background focus due to another higher priority channels exist.
                    setChannelFocus(channelToAcquire, FocusState.BACKGROUND)
                } else {
                    setChannelFocus(currentForegroundChannel, FocusState.BACKGROUND)
                    setChannelFocus(channelToAcquire, FocusState.FOREGROUND)
                }
            }
            else -> {
                setChannelFocus(channelToAcquire, FocusState.BACKGROUND)
            }
        }
    }

    private fun removeActiveChannel(channel: Channel) {
        synchronized(activeChannels) {
            activeChannels.findLast { it.channel == channel }?.let {
                Logger.d(TAG, "[removeActiveChannel] remove:$it")
                activeChannels.remove(it)
            }
        }
    }

    private fun releaseChannelHelper(
        channelToRelease: Channel,
        channelObserver: ChannelObserver
    ): Boolean {
        Logger.d(TAG, "[releaseChannelHelper] ${channelToRelease}, ${channelToRelease.state.interfaceName}")
        if (!channelToRelease.doesObserverOwnChannel(channelObserver)) {
            Logger.d(TAG, "[releaseChannelHelper] not matched current observer")
            return false
        }

        val wasForegrounded = foregroundChannel == channelToRelease
        removeActiveChannel(channelToRelease)

        setChannelFocus(channelToRelease, FocusState.NONE)
        if (wasForegrounded) {
            foregroundChannel = null
            foregroundHighestPriorityActiveChannel()
        }
        return true
    }

    private fun stopForegroundActivityHelper(foregroundChannel: Channel, foregroundChannelInterfaceName: String) {
        if (foregroundChannelInterfaceName != foregroundChannel.getInterfaceName()) {
            return
        }

        if (foregroundChannel.hasObserver() == false) {
            return
        }

        setChannelFocus(foregroundChannel, FocusState.NONE)
        removeActiveChannel(foregroundChannel)
        foregroundHighestPriorityActiveChannel()
    }

    private fun setChannelFocus(channel: Channel, focus: FocusState) {
        Logger.d(TAG, "[setChannelFocus] $channel, $focus")
        // if foreground focus requested, then acquire external focus if need.
        externalFocusInteractor?.let {
            if(focus == FocusState.FOREGROUND) {
                if(!it.acquire(channel.name, channel.getInterfaceName())) {
                    // TODO: ignore currently. later, have to handle this case.
                }
            }
        }

        if (!channel.setFocus(focus)) {
            return
        }

        if(focus == FocusState.FOREGROUND) {
            foregroundChannel = channel
        }

        // if loss focus, then release external focus also.
        externalFocusInteractor?.let {
            if(focus == FocusState.NONE) {
                it.release(channel.name, channel.getInterfaceName())
            }
        }

        channelConfigurations.find { it.name == channel.name }?.let { config ->
            listeners.forEach { it.onFocusChanged(config, focus, channel.state.interfaceName) }
        }
    }

    private fun getChannel(channelName: String): Channel? = allChannels[channelName]

    private fun getHighestPriorityActiveChannel(): ActiveChannel? =
        synchronized(activeChannels) { activeChannels.lastOrNull() }

    private fun foregroundHighestPriorityActiveChannel() {
        val channelToForeground = getHighestPriorityActiveChannel()
        Logger.d(TAG, "[foregroundHighestPriorityActiveChannel] ${channelToForeground?.channel?.name}, $activeChannels")
        if (channelToForeground != null) {
            setChannelFocus(channelToForeground.channel, FocusState.FOREGROUND)
        } else {
            Logger.d(TAG, "[foregroundHighestPriorityActiveChannel] non channel to foreground.")
        }
    }

    override fun addListener(listener: FocusManagerInterface.OnFocusChangedListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: FocusManagerInterface.OnFocusChangedListener) {
        listeners.remove(listener)
    }

    override fun setExternalFocusInteractor(focusInteractor: FocusManagerInterface.ExternalFocusInteractor) {
        externalFocusInteractor = focusInteractor
    }
}