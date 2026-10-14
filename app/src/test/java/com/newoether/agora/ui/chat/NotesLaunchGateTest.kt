package com.newoether.agora.ui.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class NotesLaunchGateTest {
    @Test fun completedLaunchDoesNotNeedAnActivityResumeBeforeTheNextTap() = runTest {
        val gate = NotesLaunchGate(); var launches = 0
        repeat(2) { assertTrue(gate.run { launches++ }) }
        assertEquals(2, launches)
    }
    @Test fun duplicateTapIsBlockedOnlyWhileDispatchIsRunning() = runTest {
        val gate = NotesLaunchGate(); val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val pending = launch { gate.run { started.complete(Unit); finish.await() } }
        started.await()
        assertFalse(gate.run { fail("Duplicate dispatch") })
        finish.complete(Unit); pending.join()
        assertTrue(gate.run {})
    }
    @Test fun cancellationAndFailureReleaseTheDispatchGate() = runTest {
        val gate = NotesLaunchGate(); val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val pending = launch { gate.run { started.complete(Unit); finish.await() } }
        started.await(); pending.cancel(); pending.join()
        assertTrue(gate.run {})
        try { gate.run { throw IllegalStateException("failed") }; fail("Failure should propagate") }
        catch (_: IllegalStateException) {}
        assertTrue(gate.run {})
    }
}
