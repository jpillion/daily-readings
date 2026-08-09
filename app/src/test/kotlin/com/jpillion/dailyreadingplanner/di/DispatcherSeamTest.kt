package com.jpillion.dailyreadingplanner.di

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test

/**
 * p1-04 — the evidence that routing `Dispatchers.IO` through the [IoDispatcher] qualifier changed
 * **which thread nothing runs on**.
 *
 * This repo runs StrictMode in debug and holds a standing rule that all I/O is off the main
 * thread. That rule is enforced at runtime by StrictMode, which no JVM test can observe, so the
 * substitute is an identity assertion: the qualifier still hands out the very same dispatcher
 * object the call sites used to name inline. If a later change makes `@IoDispatcher` mean anything
 * else on Android, this goes red before a device does.
 *
 * The port's reason for the qualifier is separate and worth restating: `Dispatchers.IO` does not
 * exist on Kotlin/Native, and it compiles fine on Android, so an inline reference fails at a phase
 * boundary months later rather than at the keyboard.
 */
class DispatcherSeamTest {
    @Test
    fun `the IoDispatcher qualifier still hands out Dispatchers IO itself on Android`() {
        assertThat(DispatcherModule.provideIoDispatcher()).isSameInstanceAs(Dispatchers.IO)
    }

    @Test
    fun `the DefaultDispatcher qualifier still hands out Dispatchers Default`() {
        assertThat(DispatcherModule.provideDefaultDispatcher()).isSameInstanceAs(Dispatchers.Default)
    }

    /**
     * The provided dispatcher really does move work off the caller's thread. An injected
     * dispatcher that silently ran inline would keep every unit test green while putting disk I/O
     * back on whatever thread called it — on a device, the main one.
     */
    @Test
    fun `work submitted to the provided IO dispatcher leaves the calling thread`() {
        val io = DispatcherModule.provideIoDispatcher()
        val caller = Thread.currentThread().name
        val ran = runBlocking { withContext(io) { Thread.currentThread().name } }
        assertThat(ran == caller).isEqualTo(false)
    }
}
