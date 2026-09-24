package com.josemilk.kimicode.android.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ApprovalManager {
    private val lock = Mutex()
    private val pending = mutableMapOf<String, CompletableDeferred<Boolean>>()

    suspend fun request(request: ToolRequest): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        lock.withLock { pending[request.id] = deferred }
        return try { deferred.await() } finally { lock.withLock { pending.remove(request.id) } }
    }

    suspend fun approve(id: String) = lock.withLock { pending.remove(id)?.complete(true) ?: false }
    suspend fun deny(id: String) = lock.withLock { pending.remove(id)?.complete(false) ?: false }
}
