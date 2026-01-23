package tibarj.tranquilstopwatch.model

import java.util.concurrent.atomic.AtomicLong

data class Beeper(
    val enabled: Boolean = true,
    val frequencyHz: String = "",
    val durationMs: String = "",
    val attenuationDb: String = "",
    val afterSec: String = "",
    val repeatTimes: String = "",
    val periodicitySec: String = "",
    val offsetSec: String = "",
    val waveform: String = "sine",
    val id: Long = nextId()
) {
    companion object {
        private val idGenerator = AtomicLong(System.currentTimeMillis())

        fun nextId(): Long = idGenerator.incrementAndGet()

        // Validation ranges
        const val MIN_FREQUENCY_HZ = 10
        const val MAX_FREQUENCY_HZ = 9999
        const val MIN_DURATION_MS = 30
        const val MAX_DURATION_MS = 999
        const val MIN_ATTENUATION_DB = -100
        const val MAX_ATTENUATION_DB = 0
        const val MIN_AFTER_SEC = 0L
        const val MAX_AFTER_SEC = 86400L
        const val MIN_REPEAT_TIMES = 0
        const val MAX_REPEAT_TIMES = 1000
        const val MIN_PERIODICITY_SEC = 1L
        const val MAX_PERIODICITY_SEC = 86400L
        const val MIN_OFFSET_SEC = 0L
        const val MAX_OFFSET_SEC = 3600L
    }

    /**
     * Validate and clamp frequency to valid range
     */
    fun getValidFrequencyHz(): Int {
        return (frequencyHz.toIntOrNull() ?: 1000).coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ)
    }

    /**
     * Validate and clamp duration to valid range
     */
    fun getValidDurationMs(): Int {
        return (durationMs.toIntOrNull() ?: 500).coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
    }

    /**
     * Validate and clamp attenuation to valid range
     */
    fun getValidAttenuationDb(): Int {
        return (attenuationDb.toIntOrNull() ?: -6).coerceIn(MIN_ATTENUATION_DB, MAX_ATTENUATION_DB)
    }

    /**
     * Validate and clamp afterSec to valid range
     */
    fun getValidAfterSec(): Long {
        return (afterSec.toLongOrNull() ?: 0L).coerceIn(MIN_AFTER_SEC, MAX_AFTER_SEC)
    }

    /**
     * Validate and clamp repeatTimes (0 = infinite)
     */
    fun getValidRepeatTimes(): Int {
        val repeat = repeatTimes.toIntOrNull() ?: 0
        return if (repeat == 0) Int.MAX_VALUE else repeat.coerceIn(MIN_REPEAT_TIMES, MAX_REPEAT_TIMES)
    }

    /**
     * Validate and clamp periodicity to valid range
     */
    fun getValidPeriodicitySec(): Long {
        return (periodicitySec.toLongOrNull() ?: 60L).coerceIn(MIN_PERIODICITY_SEC, MAX_PERIODICITY_SEC)
    }

    /**
     * Validate and clamp offset to valid range
     */
    fun getValidOffsetSec(): Long {
        return (offsetSec.toLongOrNull() ?: 0L).coerceIn(MIN_OFFSET_SEC, MAX_OFFSET_SEC)
    }

    /**
     * Get validated waveform (defaults to sine if invalid)
     */
    fun getValidWaveform(): String {
        return if (waveform in listOf("sine", "square", "triangle", "sawtooth")) {
            waveform
        } else {
            "sine"
        }
    }
}
