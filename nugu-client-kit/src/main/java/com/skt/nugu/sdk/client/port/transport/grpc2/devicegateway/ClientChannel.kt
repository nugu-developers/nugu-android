package com.skt.nugu.sdk.client.port.transport.grpc2.devicegateway

import devicegateway.grpc.Upstream
import io.grpc.stub.StreamObserver
import java.util.concurrent.ScheduledFuture

internal data class ClientChannel (
    var clientCall : StreamObserver<Upstream>?,
    var scheduledFuture: ScheduledFuture<*>?,
    val responseObserver : EventsService.ClientCallStreamObserver
)