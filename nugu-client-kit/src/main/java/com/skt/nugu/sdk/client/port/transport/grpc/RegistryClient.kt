package com.skt.nugu.sdk.client.port.transport.grpc

import com.skt.nugu.sdk.client.port.transport.grpc.utils.BackOff
import com.skt.nugu.sdk.core.utils.Logger
import devicegateway.grpc.PolicyRequest
import devicegateway.grpc.PolicyResponse
import devicegateway.grpc.RegistryGrpc
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.util.concurrent.ScheduledThreadPoolExecutor

internal class RegistryClient {
    companion object {
        private const val TAG = "RegistryClient"
        fun newClient() = RegistryClient()
    }
    private var backoff : BackOff = BackOff.DEFAULT()

    var policy: PolicyResponse? = null
    private var state: Enum<State> = State.POLICY_INIT

    interface Observer {
        fun onCompleted()
        fun onError(code: Status.Code)
    }

    internal enum class State {
        POLICY_INIT,
        POLICY_IN_PROGRESS,
        POLICY_FAILED,
        POLICY_COMPLETE,
    }

    fun isConnecting() = state == State.POLICY_IN_PROGRESS

    fun getPolicy(registryChannel: ManagedChannel, observer: Observer) {
        state = State.POLICY_IN_PROGRESS

        RegistryGrpc.newStub(registryChannel).getPolicy(
            PolicyRequest.newBuilder().build(),
            object : StreamObserver<PolicyResponse> {
                override fun onNext(value: PolicyResponse?) {
                    Logger.d(TAG, "[onNext] $value")
                    policy = value
                }

                override fun onError(t: Throwable?) {
                    state = State.POLICY_FAILED

                    val status = Status.fromThrowable(t)
                    //observer.onError(status.code)

                    Logger.e(TAG, "[onError] error on getPolicy($status)")

                    backoff.awaitRetry(status.code, object : BackOff.Observer {

                        override fun onError(reason: String) {
                            Logger.w(TAG, "[awaitRetry] Error : $reason")
                            observer.onError(status.code)
                        }

                        override fun onRetry(retriesAttempted: Int) {
                            getPolicy(registryChannel, observer)
                        }
                    })
                }

                override fun onCompleted() {
                    registryChannel.shutdownNow()
                    backoff.reset()
                    state = State.POLICY_COMPLETE


                    val policy = policy
                    if (policy == null) {
                        TODO("policy is null")
                    } else {
                        observer.onCompleted()
                    }
                }
            })
    }

    fun shutdown() {
        backoff.reset()
        state = State.POLICY_INIT
        policy = null
    }
}