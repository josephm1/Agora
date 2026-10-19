package com.newoether.agora.ui.chat

import java.util.concurrent.atomic.AtomicBoolean

/** Guards dispatch work only. NotesExchangeStore owns the durable session; activity resume
 * is not a reliable completion signal when a target is canceled/reused or replaced in-place. */
internal class NotesLaunchGate {
    private val dispatching = AtomicBoolean(false)
    suspend fun run(dispatch: suspend () -> Unit): Boolean {
        if (!dispatching.compareAndSet(false, true)) return false
        try { dispatch(); return true }
        finally { dispatching.set(false) }
    }
}
