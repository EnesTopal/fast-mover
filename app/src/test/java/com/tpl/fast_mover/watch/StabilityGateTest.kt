package com.tpl.fast_mover.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityGateTest {

    private val gate = StabilityGate(minStableMs = 1500)

    @Test
    fun firstObservationIsNeverStable() {
        assertFalse(gate.observe("a", 100, 1, nowMs = 0))
    }

    @Test
    fun becomesStableAfterUnchangedInterval() {
        gate.observe("a", 100, 1, nowMs = 0)
        assertFalse(gate.observe("a", 100, 1, nowMs = 1000))
        assertTrue(gate.observe("a", 100, 1, nowMs = 1500))
    }

    @Test
    fun growthResetsTheClock() {
        gate.observe("a", 100, 1, nowMs = 0)
        assertFalse(gate.observe("a", 200, 2, nowMs = 1600))   // still being written
        assertFalse(gate.observe("a", 200, 2, nowMs = 2000))
        assertTrue(gate.observe("a", 200, 2, nowMs = 3100))
    }

    @Test
    fun forgetAndRetain() {
        gate.observe("a", 1, 1, 0)
        gate.observe("b", 1, 1, 0)
        gate.retainOnly(setOf("b"))
        assertEquals(1, gate.size)
        gate.forget("b")
        assertEquals(0, gate.size)
        assertFalse(gate.observe("b", 1, 1, 5000))   // forgotten → first observation again
    }
}
