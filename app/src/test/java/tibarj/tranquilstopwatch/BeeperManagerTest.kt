package tibarj.tranquilstopwatch

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for BeeperManager calculation methods.
 * Tests the core scheduling algorithms using static companion methods.
 */
class BeeperManagerTest {

    // ========================================================================
    // calculateOccurrences() Tests - No Initial Delay (afterSec = 0)
    // ========================================================================

    @Test
    fun calculateOccurrences_noDelay_beforeFirstBeep() {
        // At 30s with 60s period: not yet reached first beep at 60s
        assertEquals(0L, BeeperManager.calculateOccurrences(30, 0, 60))
    }

    @Test
    fun calculateOccurrences_noDelay_atFirstBeep() {
        // At exactly 60s with 60s period: first beep occurs
        assertEquals(1L, BeeperManager.calculateOccurrences(60, 0, 60))
    }

    @Test
    fun calculateOccurrences_noDelay_afterMultipleBeeps() {
        // At 180s with 60s period: 3 beeps have occurred (60s, 120s, 180s)
        assertEquals(3L, BeeperManager.calculateOccurrences(180, 0, 60))

        // At 150s with 60s period: 2 beeps have occurred (60s, 120s)
        assertEquals(2L, BeeperManager.calculateOccurrences(150, 0, 60))
    }

    @Test
    fun calculateOccurrences_noDelay_partialPeriod() {
        // At 125s with 60s period: only 2 complete periods (60s, 120s)
        assertEquals(2L, BeeperManager.calculateOccurrences(125, 0, 60))
    }

    // ========================================================================
    // calculateOccurrences() Tests - With Initial Delay (afterSec > 0)
    // ========================================================================

    @Test
    fun calculateOccurrences_withDelay_beforeDelay() {
        // At 20s with 30s delay: no beeps yet
        assertEquals(0L, BeeperManager.calculateOccurrences(20, 30, 60))
    }

    @Test
    fun calculateOccurrences_withDelay_atFirstBeep() {
        // At exactly 30s with 30s delay: first beep occurs
        assertEquals(1L, BeeperManager.calculateOccurrences(30, 30, 60))
    }

    @Test
    fun calculateOccurrences_withDelay_afterFirstBeep() {
        // At 50s with 30s delay and 60s period: only first beep has occurred
        assertEquals(1L, BeeperManager.calculateOccurrences(50, 30, 60))
    }

    @Test
    fun calculateOccurrences_withDelay_atSecondBeep() {
        // At 90s with 30s delay and 60s period: 2 beeps (30s, 90s)
        assertEquals(2L, BeeperManager.calculateOccurrences(90, 30, 60))
    }

    @Test
    fun calculateOccurrences_withDelay_afterMultipleBeeps() {
        // At 150s with 30s delay and 60s period: 3 beeps (30s, 90s, 150s)
        assertEquals(3L, BeeperManager.calculateOccurrences(150, 30, 60))

        // At 210s with 30s delay and 60s period: 4 beeps (30s, 90s, 150s, 210s)
        assertEquals(4L, BeeperManager.calculateOccurrences(210, 30, 60))
    }

    // ========================================================================
    // calculateNextBeepTime() Tests - No Initial Delay (afterSec = 0)
    // ========================================================================

    @Test
    fun calculateNextBeepTime_noDelay_firstBeep() {
        // After 0 occurrences, next beep at 1 * period
        assertEquals(60L, BeeperManager.calculateNextBeepTime(0, 0, 60))
    }

    @Test
    fun calculateNextBeepTime_noDelay_secondBeep() {
        // After 1 occurrence, next beep at 2 * period
        assertEquals(120L, BeeperManager.calculateNextBeepTime(1, 0, 60))
    }

    @Test
    fun calculateNextBeepTime_noDelay_subsequentBeeps() {
        // After 2 occurrences, next beep at 3 * period
        assertEquals(180L, BeeperManager.calculateNextBeepTime(2, 0, 60))

        // After 5 occurrences, next beep at 6 * period
        assertEquals(360L, BeeperManager.calculateNextBeepTime(5, 0, 60))
    }

    @Test
    fun calculateNextBeepTime_noDelay_differentPeriod() {
        // Test with 15-minute (900s) period
        assertEquals(900L, BeeperManager.calculateNextBeepTime(0, 0, 900))
        assertEquals(1800L, BeeperManager.calculateNextBeepTime(1, 0, 900))
    }

    // ========================================================================
    // calculateNextBeepTime() Tests - With Initial Delay (afterSec > 0)
    // ========================================================================

    @Test
    fun calculateNextBeepTime_withDelay_firstBeep() {
        // After 0 occurrences, next beep is at the initial delay
        assertEquals(30L, BeeperManager.calculateNextBeepTime(0, 30, 60))
    }

    @Test
    fun calculateNextBeepTime_withDelay_secondBeep() {
        // After 1 occurrence, next beep is delay + 1 * period
        assertEquals(90L, BeeperManager.calculateNextBeepTime(1, 30, 60))
    }

    @Test
    fun calculateNextBeepTime_withDelay_subsequentBeeps() {
        // After 2 occurrences, next beep is delay + 2 * period
        assertEquals(150L, BeeperManager.calculateNextBeepTime(2, 30, 60))

        // After 3 occurrences, next beep is delay + 3 * period
        assertEquals(210L, BeeperManager.calculateNextBeepTime(3, 30, 60))
    }

    @Test
    fun calculateNextBeepTime_withDelay_differentValues() {
        // Test with 5s delay and 100s period
        assertEquals(5L, BeeperManager.calculateNextBeepTime(0, 5, 100))
        assertEquals(105L, BeeperManager.calculateNextBeepTime(1, 5, 100))
        assertEquals(205L, BeeperManager.calculateNextBeepTime(2, 5, 100))
    }

    // ========================================================================
    // Edge Case Tests
    // ========================================================================

    @Test
    fun calculateOccurrences_edgeCase_exactMultiple() {
        // Test exact multiples work correctly
        assertEquals(10L, BeeperManager.calculateOccurrences(600, 0, 60))
    }

    @Test
    fun calculateNextBeepTime_edgeCase_largeNumbers() {
        // Test with 24-hour period (86400s)
        assertEquals(86400L, BeeperManager.calculateNextBeepTime(0, 0, 86400))
        assertEquals(172800L, BeeperManager.calculateNextBeepTime(1, 0, 86400))
    }

    @Test
    fun calculateOccurrences_edgeCase_minimumPeriod() {
        // Test with minimum allowed period (60s)
        assertEquals(10L, BeeperManager.calculateOccurrences(600, 0, 60))
    }

    // ========================================================================
    // Integration Test Scenarios
    // ========================================================================

    @Test
    fun scenario_15minBeep_1hour() {
        // 15-minute beep (900s period), at 1 hour (3600s)
        val occurrences = BeeperManager.calculateOccurrences(3600, 0, 900)
        assertEquals(4L, occurrences) // 900s, 1800s, 2700s, 3600s

        val nextBeep = BeeperManager.calculateNextBeepTime(occurrences, 0, 900)
        assertEquals(4500L, nextBeep) // Next at 75 minutes
    }

    @Test
    fun scenario_hourlyBeep_1hour() {
        // Hourly beep (3600s period), at 1 hour (3600s)
        val occurrences = BeeperManager.calculateOccurrences(3600, 0, 3600)
        assertEquals(1L, occurrences) // First beep at 3600s

        val nextBeep = BeeperManager.calculateNextBeepTime(occurrences, 0, 3600)
        assertEquals(7200L, nextBeep) // Next at 2 hours
    }

    @Test
    fun scenario_delayedStart_30secDelay_60secPeriod() {
        // Beep at 30s, then every 60s after
        assertEquals(0L, BeeperManager.calculateOccurrences(20, 30, 60))
        assertEquals(1L, BeeperManager.calculateOccurrences(30, 30, 60))
        assertEquals(1L, BeeperManager.calculateOccurrences(60, 30, 60))
        assertEquals(2L, BeeperManager.calculateOccurrences(90, 30, 60))
        assertEquals(2L, BeeperManager.calculateOccurrences(120, 30, 60))
        assertEquals(3L, BeeperManager.calculateOccurrences(150, 30, 60))
    }
}
