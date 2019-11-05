package com.skt.nugu.sdk.client.port.transport.grpc

import com.skt.nugu.sdk.core.interfaces.auth.AuthDelegate
import com.skt.nugu.sdk.core.interfaces.message.MessageConsumer
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.transport.TransportFactory
import com.skt.nugu.sdk.core.interfaces.transport.TransportListener

/**
 * TransportFactory to create [GrpcTransport].
 * @param option the options for GrpcTransport
 */
class GrpcTransportFactory(private val option: Options = Options()) : TransportFactory {
    /**
     * Create a Transport.
     */
    override fun createTransport(
        authDelegate: AuthDelegate,
        messageConsumer: MessageConsumer,
        transportObserver: TransportListener
    ): Transport {
        return GrpcTransport.create(
            option,
            authDelegate,
            messageConsumer,
            transportObserver
        )
    }
}
