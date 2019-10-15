package com.skt.nugu.sdk.core.network

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.skt.nugu.sdk.core.interfaces.connection.ConnectionStatusListener
import com.skt.nugu.sdk.core.interfaces.message.MessageObserver
import org.junit.Test

class NetworkManagerTest {
    private val messageRouter: MessageRouterInterface
    private val connectionStatusObserver: ConnectionStatusListener
    private val messageObserver: MessageObserver
    private val connectionManager: NetworkManager

    init {
        messageRouter = mock()
        connectionStatusObserver = mock()
        messageObserver = mock()
        connectionManager = NetworkManager.create(messageRouter)
    }

    @Test
    fun createTest() {
        verify(messageRouter, times(1)).setObserver(connectionManager)
    }
}