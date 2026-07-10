package com.auroraclima.app.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionThrottleTest {
    private class FakeClock(var nowMs: Long = 0L) : MonotonicClock {
        override fun nowMs(): Long = nowMs
    }

    @Test
    fun `el primer llamado siempre registra`() {
        val throttle = InteractionThrottle(FakeClock())

        assertTrue(throttle.shouldRegister())
    }

    @Test
    fun `dentro del intervalo no vuelve a registrar`() {
        val clock = FakeClock()
        val throttle = InteractionThrottle(clock)
        throttle.shouldRegister()

        clock.nowMs += InteractionThrottle.MIN_INTERVAL_MS - 1

        assertFalse(throttle.shouldRegister())
    }

    @Test
    fun `cumplido el intervalo registra de nuevo`() {
        val clock = FakeClock()
        val throttle = InteractionThrottle(clock)
        throttle.shouldRegister()

        clock.nowMs += InteractionThrottle.MIN_INTERVAL_MS

        assertTrue(throttle.shouldRegister())
    }

    @Test
    fun `un intento rechazado no reinicia el intervalo`() {
        val clock = FakeClock()
        val throttle = InteractionThrottle(clock)
        throttle.shouldRegister()

        clock.nowMs += InteractionThrottle.MIN_INTERVAL_MS - 1
        throttle.shouldRegister()
        clock.nowMs += 1

        assertTrue(throttle.shouldRegister())
    }
}
