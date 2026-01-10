package tibarj.tranquilstopwatch

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import tibarj.tranquilstopwatch.model.Beeper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Manages beeper playback based on stopwatch elapsed time.
 * Loads beeper configurations and triggers audio beeps at specified intervals.
 */
class BeeperManager(private val context: Context) {

    private val tag = "BeeperManager"
    private val beepers = mutableListOf<Beeper>()
    private val audioExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Callback for error notifications */
    var onError: ((message: String) -> Unit)? = null

    companion object {
        const val SAMPLE_RATE = 22050
        const val MAX_AMPLITUDE = 32767
        const val SECONDS_PER_DAY = 86400L
        const val MAX_BEEPERS = 10

        // Smooths the start/end of each beep to avoid clicks.
        const val ATTACK_RELEASE_MS = 7

        /**
         * Calculate how many times a beeper has occurred by a given elapsed time.
         * Extracted for testability and clarity.
         *
         * @param currentElapsedSec Current elapsed time in seconds
         * @param afterSec Initial delay before first beep (0 = no delay)
         * @param periodicitySec Time between beeps in seconds
         * @param offsetSec Offset within each period (0 = no offset)
         * @return Number of beeps that have occurred (0 if none yet)
         */
        fun calculateOccurrences(currentElapsedSec: Long, afterSec: Long, periodicitySec: Long, offsetSec: Long = 0L): Long {
            return if (afterSec == 0L) {
                // No initial delay: beeps at period+offset, 2*period+offset, 3*period+offset...
                val firstBeepTime = periodicitySec + offsetSec
                if (currentElapsedSec <= firstBeepTime) 0L
                else 1 + ((currentElapsedSec - firstBeepTime) / periodicitySec)
            } else {
                // With initial delay: first at after+offset, then after+offset+period, after+offset+2*period...
                val firstBeepTime = afterSec + offsetSec
                if (currentElapsedSec <= firstBeepTime) 0L
                else 1 + ((currentElapsedSec - firstBeepTime) / periodicitySec)
            }
        }

        /**
         * Calculate the time (in seconds) of the next beep occurrence.
         * Extracted for testability and clarity.
         *
         * @param occurrencesSoFar Number of beeps that have already occurred
         * @param afterSec Initial delay before first beep (0 = no delay)
         * @param periodicitySec Time between beeps in seconds
         * @param offsetSec Offset within each period (0 = no offset)
         * @return Time of next beep in seconds
         */
        fun calculateNextBeepTime(occurrencesSoFar: Long, afterSec: Long, periodicitySec: Long, offsetSec: Long = 0L): Long {
            // The occurrencesSoFar represents how many have already happened (0, 1, 2, ...)
            // The next beep is at the time of occurrence number occurrencesSoFar
            // For offset=1, period=60, after=0:
            //   - occurrencesSoFar=0: next at 61s (60+1, first beep)
            //   - occurrencesSoFar=1: next at 121s (120+1, second beep)
            // For offset=0, period=60, after=0:
            //   - occurrencesSoFar=0: next at 60s (first beep)
            //   - occurrencesSoFar=1: next at 120s (second beep)
            return if (afterSec == 0L) {
                // No initial delay: beeps at period+offset, 2*period+offset, 3*period+offset...
                val firstBeepTime = periodicitySec + offsetSec
                firstBeepTime + (periodicitySec * occurrencesSoFar)
            } else {
                // With initial delay: beeps at after+offset, after+offset+period, after+offset+2*period...
                afterSec + offsetSec + (periodicitySec * occurrencesSoFar)
            }
        }
    }

    /**
     * Cache for pre-generated beep samples
     * Key: "frequency_duration_attenuation_waveform"
     */
    private val beepCache = mutableMapOf<String, ShortArray>()

    /**
     * Data class containing all information needed to play a single beep.
     * Immutable to ensure thread safety during audio playback.
     *
     * @property frequencyHz Tone frequency in Hz (validated range: 10-10000)
     * @property durationMs Beep duration in milliseconds (validated range: 1-999)
     * @property attenuationDb Volume attenuation in dB (validated range: -100 to 0)
     * @property waveform Wave shape: "sine", "square", "triangle", or "sawtooth"
     *
     * Example:
     * BeepSound(1000, 500, -6, "sine") -> 1kHz sine wave, 500ms, -6dB attenuation
     */
    data class BeepSound(
        val frequencyHz: Int,
        val durationMs: Int,
        val attenuationDb: Int,
        val waveform: String
    )

    /**
     * Load beepers from SharedPreferences with backward compatibility.
     * Clears both beepers list and beep sample cache.
     *
     * Storage formats (in order of preference):
     * 1. JSON format ("beepers_json" key): Predictable ordering, all fields as JSON objects
     * 2. CSV format ("beepers" StringSet key): Legacy format, unpredictable order
     *
     * See BeeperActivity.loadBeepers() for detailed format specification.
     */
    fun loadBeepers() {
        Log.d(tag, "loadBeepers")
        val prefs = context.getSharedPreferences("BeeperActivity", Context.MODE_PRIVATE)
        beepers.clear()
        beepCache.clear()

        // Try new JSON format first
        val jsonString = prefs.getString("beepers_json", null)
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    beepers.add(Beeper(
                        enabled = json.getBoolean("enabled"),
                        frequencyHz = json.getString("frequencyHz"),
                        durationMs = json.getString("durationMs"),
                        attenuationDb = json.getString("attenuationDb"),
                        afterSec = json.getString("afterSec"),
                        repeatTimes = json.getString("repeatTimes"),
                        periodicitySec = json.getString("periodicitySec"),
                        offsetSec = json.getString("offsetSec"),
                        waveform = json.getString("waveform"),
                        id = if (json.has("id")) json.getLong("id") else Beeper.nextId()
                    ))
                }
            } catch (e: Exception) {
                Log.e(tag, "Error parsing JSON beepers: ${e.message}")
            }
        } else {
            // Fallback to old StringSet format for backward compatibility
            val beeperStrings = prefs.getStringSet("beepers", emptySet())
            beeperStrings?.forEach { str ->
                val parts = str.split(",")
                if (parts.size >= 6) {
                    beepers.add(Beeper(
                        enabled = parts[0].toBoolean(),
                        frequencyHz = parts[1],
                        durationMs = parts[2],
                        attenuationDb = parts[3],
                        afterSec = parts[4],
                        repeatTimes = parts[5],
                        periodicitySec = if (parts.size >= 7) parts[6] else parts[4],
                        offsetSec = "0",
                        waveform = if (parts.size >= 8) parts[7] else "sine"
                    ))
                }
            }
        }
        Log.d(tag, "Loaded ${beepers.size} beepers")
    }

    /**
     * Calculate the next beep time and all beep sounds scheduled for that time.
     * Calculates on-demand based on elapsed time and beeper configurations.
     *
     * Algorithm Overview:
     * For each enabled beeper:
     * 1. Validate all parameters using Beeper.getValid*() methods
     * 2. Calculate occurrencesSoFar based on elapsed time:
     *    - No initial delay (afterSec=0): floor(currentElapsed / period)
     *    - With delay: 0 if before delay, else 1 + floor((elapsed - after) / period)
     * 3. Skip if repeatTimes limit reached
     * 4. Calculate next occurrence time:
     *    - No delay: period * (occurrences + 1)
     *    - With delay: after (if first), else after + (period * occurrences)
     * 5. Track earliest future beep time
     * 6. Collect all beepers scheduled at earliest time (simultaneous beeps)
     *
     * Test Cases:
     * - afterSec=0, period=60: beeps at 60s, 120s, 180s...
     * - afterSec=30, period=60: beeps at 30s, 90s, 150s...
     * - repeatTimes=3: beeps exactly 3 times then stops
     * - Multiple beepers at same time: returns list of multiple BeepSounds
     * - All beepers exhausted: returns null
     *
     * @param currentElapsedMs Current stopwatch elapsed time in milliseconds
     * @return Pair of (next beep time in milliseconds, list of beep sounds) or null if no beeps scheduled
     */
    fun next(currentElapsedMs: Long): Pair<Long, List<BeepSound>>? {
        val currentElapsedSec = currentElapsedMs / 1000
        var earliestBeepTimeMs: Long? = null
        val beepsAtEarliestTime = mutableListOf<BeepSound>()

        beepers.forEach { beeper ->
            if (!beeper.enabled) return@forEach

            val validAfterSec = beeper.getValidAfterSec()
            val validPeriodicitySec = beeper.getValidPeriodicitySec()
            val validRepeatTimes = beeper.getValidRepeatTimes()
            val validFrequencyHz = beeper.getValidFrequencyHz()
            val validDurationMs = beeper.getValidDurationMs()
            val validAttenuationDb = beeper.getValidAttenuationDb()
            val validWaveform = beeper.getValidWaveform()
            val validOffsetSec = beeper.getValidOffsetSec()

            // Calculate how many times this beeper should have beeped by now
            val occurrencesSoFar = calculateOccurrences(currentElapsedSec, validAfterSec, validPeriodicitySec, validOffsetSec)

            // Check if we've reached the repeat limit
            if (occurrencesSoFar >= validRepeatTimes) return@forEach

            // Calculate next beep time
            val nextBeepTimeSec = calculateNextBeepTime(occurrencesSoFar, validAfterSec, validPeriodicitySec, validOffsetSec)
            val nextBeepTimeMs = nextBeepTimeSec * 1000

            // Only consider if it's in the future
            if (nextBeepTimeMs < currentElapsedMs) return@forEach

            // Track the earliest beep time
            if (earliestBeepTimeMs == null || nextBeepTimeMs < earliestBeepTimeMs!!) {
                earliestBeepTimeMs = nextBeepTimeMs
                beepsAtEarliestTime.clear()
                beepsAtEarliestTime.add(BeepSound(validFrequencyHz, validDurationMs, validAttenuationDb, validWaveform))
            } else if (nextBeepTimeMs == earliestBeepTimeMs) {
                // Multiple beepers at the same time
                beepsAtEarliestTime.add(BeepSound(validFrequencyHz, validDurationMs, validAttenuationDb, validWaveform))
            }
        }

        return if (earliestBeepTimeMs != null) {
            Pair(earliestBeepTimeMs!!, beepsAtEarliestTime)
        } else {
            null
        }
    }

    /**
     * Play a single beep sound asynchronously.
     * Delegates to playBeep() which runs on background thread via ExecutorService.
     *
     * @param beepSound The beep configuration to play (validated parameters assumed)
     */
    fun beep(beepSound: BeepSound) {
        Log.d(tag, "beep: { frequencyHz: ${beepSound.frequencyHz}, durationMs: ${beepSound.durationMs}, attenuationDb: ${beepSound.attenuationDb}, waveform: ${beepSound.waveform} }")
        playBeep(beepSound.frequencyHz, beepSound.durationMs, beepSound.attenuationDb, beepSound.waveform)
    }

    /**
     * Reset beeper state and clear cache.
     * Called when stopwatch is reset to initial state.
     *
     * Clears:
     * - beepCache: Removes all pre-generated audio samples
     *
     * Does NOT clear:
     * - beepers list: Beeper configurations are preserved
     * - Calculated schedules: These are recalculated on-demand by next()
     */
    fun reset() {
        Log.d(tag, "reset")
        beepCache.clear()
        // No state to clear - next() calculates on-demand
    }

    /**
     * Cleanup resources and shutdown audio thread pool.
     * Must be called when BeeperManager is no longer needed to prevent resource leaks.
     *
     * Call this from:
     * - Activity.onDestroy()
     * - Fragment.onDestroy()
     * - Service.onDestroy()
     *
     * After calling cleanup(), this BeeperManager instance should not be used.
     */
    fun cleanup() {
        Log.d(tag, "cleanup")
        audioExecutor.shutdown()
    }

    /**
     * Calculate how many times a beeper has occurred by a given elapsed time.
     * Extracted for testability and clarity.
     *
     * @param currentElapsedSec Current elapsed time in seconds
     * @param afterSec Initial delay before first beep (0 = no delay)
     * @param periodicitySec Time between beeps in seconds
     * @return Number of beeps that have occurred (0 if none yet)
     */
    internal fun calculateOccurrences(currentElapsedSec: Long, afterSec: Long, periodicitySec: Long): Long {
        return if (afterSec == 0L) {
            // No initial delay: beeps at period, 2*period, 3*period...
            if (currentElapsedSec < periodicitySec) 0L
            else currentElapsedSec / periodicitySec
        } else {
            // With initial delay: first at after, then after+period, after+2*period...
            if (currentElapsedSec < afterSec) 0L
            else 1 + ((currentElapsedSec - afterSec) / periodicitySec)
        }
    }

    /**
     * Calculate the time (in seconds) of the next beep occurrence.
     * Extracted for testability and clarity.
     *
     * @param occurrencesSoFar Number of beeps that have already occurred
     * @param afterSec Initial delay before first beep (0 = no delay)
     * @param periodicitySec Time between beeps in seconds
     * @return Time of next beep in seconds
     */
    internal fun calculateNextBeepTime(occurrencesSoFar: Long, afterSec: Long, periodicitySec: Long): Long {
        return if (afterSec == 0L) {
            // No initial delay
            periodicitySec * (occurrencesSoFar + 1)
        } else {
            // With initial delay
            if (occurrencesSoFar == 0L) {
                afterSec // First beep
            } else {
                afterSec + (periodicitySec * occurrencesSoFar)
            }
        }
    }

    /**
     * Calculate equal-loudness compensation based on frequency.
     * Psychoacoustic approximation (commonly used equal-loudness style curve).
     *
     * IMPORTANT: this is intentionally bounded to avoid over-correcting on
     * different phone speakers / headphones.
     *
     * Convention: returned value is added to the user's attenuation.
     * - positive => boost (less attenuation)
     * - negative => cut (more attenuation)
     */
    private fun getEqualLoudnessCompensation(frequencyHz: Int): Double {
        val f = frequencyHz / 1000.0 // Normalize to kHz

        // Common polynomial-style curve: bass needs boost, 3–4 kHz needs cut.
        // (This is NOT a strict ISO 226 implementation; it's a practical approximation.)
        val rawCompensationDb =
            3.64 * f.pow(-0.8) -
                6.5 * exp(-0.6 * (f - 3.3).pow(2.0)) +
                0.001 * f.pow(4.0)

        // Bound correction to keep it predictable across devices.
        val maxBoostDb = 3.0
        val maxCutDb = 6.0
        return rawCompensationDb.coerceIn(-maxCutDb, maxBoostDb)
    }

    /**
     * Play a single beep tone using AudioTrack
     * @param frequencyHz Frequency of the beep in Hz
     * @param durationMs Duration of the beep in milliseconds
     * @param attenuationDb Volume attenuation in dB (negative value, e.g., -6)
     * @param waveform Waveform type: "sine", "square", "triangle", "sawtooth"
     */
    private fun playBeep(frequencyHz: Int, durationMs: Int, attenuationDb: Int, waveform: String) {
        audioExecutor.execute {
            var audioTrack: AudioTrack? = null
            try {
                val cacheKey = "${frequencyHz}_${durationMs}_${attenuationDb}_${waveform}"

                // Get cached samples or generate new ones
                val samples = beepCache.getOrPut(cacheKey) {
                    generateBeepSamples(frequencyHz, durationMs, attenuationDb, waveform, SAMPLE_RATE)
                }

                val numSamples = samples.size

                // Check preference for silent mode override
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val overrideSilent = prefs.getBoolean(
                    context.getString(R.string.beeper_override_silent_key),
                    context.resources.getBoolean(R.bool.default_beeper_override_silent)
                )

                // Use USAGE_ALARM to override silent mode, USAGE_NOTIFICATION to respect it
                val audioUsage = if (overrideSilent) {
                    AudioAttributes.USAGE_ALARM
                } else {
                    AudioAttributes.USAGE_NOTIFICATION
                }

                // Create and configure AudioTrack
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(audioUsage)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(numSamples * 2) // 2 bytes per sample (16-bit)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, numSamples)
                audioTrack.play()

                // Schedule cleanup using Handler instead of blocking thread
                val trackToRelease = audioTrack
                mainHandler.postDelayed({
                    try {
                        trackToRelease.stop()
                        trackToRelease.release()
                    } catch (e: Exception) {
                        Log.e(tag, "Error releasing AudioTrack: ${e.message}")
                    }
                }, durationMs.toLong() + 100)

                Log.d(tag, "Played beep: ${frequencyHz}Hz, ${durationMs}ms, ${attenuationDb}dB, ${waveform} (cached=${cacheKey in beepCache})")
            } catch (e: Exception) {
                Log.e(tag, "Error playing beep: ${e.message}")
                // Notify error on main thread
                mainHandler.post {
                    onError?.invoke("Beep playback failed: ${e.message}")
                }
                // Clean up on error
                audioTrack?.stop()
                audioTrack?.release()
            }
        }
    }

    /**
     * Generate beep samples for given parameters.
     *
     * Algorithm:
     * 1. Calculate sample count based on duration and sample rate
     * 2. Apply equal-loudness compensation (Fletcher-Munson curve)
     * 3. Convert dB attenuation to linear amplitude
     * 4. Generate waveform samples:
     *    - sine: sin(2π * i / samples_per_cycle)
     *    - square: sign(sin(angle))
     *    - triangle: piecewise linear ramp
     *    - sawtooth: linear ramp from -1 to 1
     * 5. Scale by amplitude and convert to 16-bit PCM
     *
     * @param frequencyHz Tone frequency (10-10000 Hz)
     * @param durationMs Duration in milliseconds (1-999 ms)
     * @param attenuationDb Volume attenuation in dB (-100 to 0)
     * @param waveform Wave shape ("sine", "square", "triangle", "sawtooth")
     * @param sampleRate Audio sample rate in Hz (typically 22050)
     * @return ShortArray of PCM samples ready for AudioTrack
     */
    private fun generateBeepSamples(frequencyHz: Int, durationMs: Int, attenuationDb: Int, waveform: String, sampleRate: Int): ShortArray {
        val numSamples = (durationMs * sampleRate) / 1000
        val samples = ShortArray(numSamples)

        // Apply a short linear attack/release envelope to avoid slope discontinuities.
        val rampSamplesRequested = (ATTACK_RELEASE_MS * sampleRate) / 1000
        val rampSamples = when {
            numSamples <= 1 -> 0
            rampSamplesRequested <= 0 -> 0
            else -> minOf(rampSamplesRequested, numSamples / 2)
        }

        // Apply equal-loudness compensation
        val compensationDb = getEqualLoudnessCompensation(frequencyHz)
        val adjustedAttenuationDb = attenuationDb.toDouble() + compensationDb

        // Calculate amplitude from attenuation (dB)
        // 0 dB = full volume (MAX_AMPLITUDE), -6 dB = half volume, etc.
        val amplitude = (MAX_AMPLITUDE * 10.0.pow(adjustedAttenuationDb / 20.0)).toInt().coerceIn(0, MAX_AMPLITUDE)

        // Generate waveform
        for (i in samples.indices) {
            val angle = 2.0 * PI * i / (sampleRate / frequencyHz.toDouble())
            val baseSample = when (waveform) {
                "square" -> if (sin(angle) >= 0) amplitude.toDouble() else -amplitude.toDouble()
                "triangle" -> {
                    // Triangle wave: linear interpolation between -1 and 1
                    val phase = (angle / (2.0 * PI)) % 1.0
                    amplitude * (if (phase < 0.5) (4.0 * phase - 1.0) else (3.0 - 4.0 * phase))
                }
                "sawtooth" -> {
                    // Sawtooth wave: linear ramp from -1 to 1
                    val phase = (angle / (2.0 * PI)) % 1.0
                    amplitude * (2.0 * phase - 1.0)
                }
                else -> sin(angle) * amplitude // "sine" or default
            }

            val envelope = if (rampSamples <= 1) {
                1.0
            } else {
                when {
                    i < rampSamples -> i.toDouble() / rampSamples.toDouble()
                    i >= numSamples - rampSamples -> (numSamples - i - 1).toDouble() / rampSamples.toDouble()
                    else -> 1.0
                }
            }

            val sample = (baseSample * envelope).toInt().coerceIn(-MAX_AMPLITUDE, MAX_AMPLITUDE)
            samples[i] = sample.toShort()
        }

        return samples
    }
}

