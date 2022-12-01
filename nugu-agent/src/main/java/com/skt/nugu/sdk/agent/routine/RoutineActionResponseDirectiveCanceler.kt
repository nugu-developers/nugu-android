package com.skt.nugu.sdk.agent.routine

import com.skt.nugu.sdk.core.interfaces.directive.DirectiveGroupPreProcessor
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveProcessorInterface
import com.skt.nugu.sdk.core.interfaces.message.Directive
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RoutineActionResponseDirectiveCanceler(
    private val directiveProcessor: DirectiveProcessorInterface
): DirectiveGroupPreProcessor {
    companion object {
        private const val TAG = "RoutineActionResponseDirectiveCanceler"
    }

    private val lock = ReentrantLock()
    // cancel 요청온것
    private val cancelRequested = LinkedHashSet<String>()

    override fun preProcess(directives: List<Directive>): List<Directive> {
        lock.withLock {
            val dialogRequestId = directives.firstOrNull()?.getDialogRequestId()
            if (dialogRequestId != null) {
                if (cancelRequested.remove(dialogRequestId)) {
                    Logger.d(TAG, "[preProcess] $dialogRequestId's directives removed by $TAG")
                    return emptyList()
                }
            }

            return directives
        }
    }

    /**
     * Should be called when action cancel requested.
     */
    fun requestCancel(dialogRequestId: String) {
        Logger.d(TAG, "[requestCancel] dialogRequestId: $dialogRequestId")
        lock.withLock {
            if(cancelRequested.size > 20) {
                cancelRequested.firstOrNull()?.let {
                    cancelRequested.remove(it)
                }
            }
            cancelRequested.add(dialogRequestId)
        }
        directiveProcessor.cancelDialogRequestId(dialogRequestId)
    }
}