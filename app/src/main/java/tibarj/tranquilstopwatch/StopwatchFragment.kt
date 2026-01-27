package tibarj.tranquilstopwatch

import android.media.AudioManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import tibarj.tranquilstopwatch.databinding.StopwatchFragmentBinding
import java.util.concurrent.TimeUnit


class StopwatchFragment : Fragment() {
    private val tag: String = "StopwatchFragment"
    private lateinit var _sharedPreferences: SharedPreferences
    private var _binding: StopwatchFragmentBinding? = null
    private var _enabled: Boolean = true
    private lateinit var _stopwatch: Stopwatch
    private lateinit var _beeperManager: BeeperManager

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(tag, "onCreateView")
        _sharedPreferences = requireContext().getSharedPreferences(
            "StopwatchFragment",
            Context.MODE_PRIVATE
        )
        _beeperManager = BeeperManager(requireContext())
        _beeperManager.onError = { message ->
            // Show toast on error
            android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
        }
        _stopwatch = Stopwatch (
            _tick = { elapsed ->
                display(elapsed)
            },
            _startedAt = _sharedPreferences.getLong("startedAt", 0L),
            _anteriority = _sharedPreferences.getLong("anteriority", 0L),
        )
        _binding = StopwatchFragmentBinding.inflate(inflater, container, false)
        initTapListeners()
        return binding.root
    }

    // visible but not interactable
    override fun onStart() {
        Log.d(tag, "onStart")
        super.onStart()

        _beeperManager.loadBeepers()
        loadPref()
        updateVolumeKeyRouting()
        logState()

        display(_stopwatch.getElapsedMs())
        if (_stopwatch.isStarted()) {
            keepScreenOn()
            setColor(R.color.white)
            _stopwatch.schedule()

            // Beep timing must be independent from the stopwatch display tick.
            // We drive it from the stopwatch elapsed time so pause/resume doesn't shift schedules.
            _beeperManager.startScheduling { _stopwatch.getElapsedMs() }
        } else {
            setColor(R.color.red)
            _beeperManager.stopScheduling()
        }
    }

    override fun onStop() {
        Log.d(tag, "onStop")
        super.onStop()

        _stopwatch.unschedule()
        // Stopwatch may continue running in background, but the UI isn't visible.
        // Stop beep scheduling so we don't play audio while backgrounded.
        _beeperManager.stopScheduling()
        // Restore default volume-key behavior when leaving this screen.
        requireActivity().volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
        unkeepScreenOn()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Prevent leaks / stray callbacks when fragment view goes away.
        _beeperManager.cleanup()
        _binding = null
    }

    private fun saveInstanceState() {
        Log.d(tag, "saveInstanceState")

        _sharedPreferences.edit {
            putLong("startedAt", _stopwatch.startedAt)
            putLong("anteriority", _stopwatch.anteriority)
        }
        logState()
    }

    private fun loadPref() {
        Log.d(tag, "loadPref")
        val pref = PreferenceManager.getDefaultSharedPreferences(requireActivity())

        _enabled = pref.getBoolean(
            getString(R.string.stopwatch_enabled_key),
            resources.getBoolean(R.bool.default_stopwatch_enabled)
        )
        if (!_enabled && _stopwatch.isStarted()) {
            stop()
        }
        if (_enabled) {
            unkeepScreenOn()
        } else {
            keepScreenOn()
        }

        _stopwatch.everySecond = pref.getBoolean(
            getString(R.string.stopwatch_show_seconds_key),
            resources.getBoolean(R.bool.default_stopwatch_show_seconds)
        )

        val customTypeface = TimeFontManager.getTypeface(requireContext())
        if (customTypeface != null) {
            Log.d(tag, "setCustomTypeface")
            binding.stopwatch.typeface = customTypeface
        } else {
            val fontFamily = "sans-serif" + if (pref.getBoolean(
                    getString(R.string.stopwatch_font_thin_key),
                    resources.getBoolean(R.bool.default_stopwatch_font_thin)
                )) "-thin" else ""
            Log.d(tag, "setFontFamily $fontFamily")
            binding.stopwatch.typeface = Typeface.create(fontFamily, Typeface.NORMAL)
        }

        val opacity = pref.getInt(
            getString(R.string.stopwatch_opacity_key),
            resources.getInteger(R.integer.default_stopwatch_opacity)
        )
        Log.d(tag, "setStopwatchOpacity $opacity")
        binding.stopwatch.alpha = opacity.toFloat() / 20f

        val size = pref.getInt(getString(R.string.stopwatch_size_key), resources.getInteger(R.integer.default_stopwatch_size))
        Log.d(tag, "setStopwatchSize $size")
        binding.stopwatch.textSize = size.toFloat()

        // Font size changes alter the layout; ask the activity to recenter after relayout.
        binding.root.post {
            (activity as? MainActivity)?.requestRecenter()
        }
    }

    private fun initTapListeners() {
        Log.d(tag, "setTapListeners")
        binding.stopwatch.setOnClickListener {
            Log.d(tag, "OnClick")
            toggle()
        }
        binding.stopwatch.setOnLongClickListener {
            Log.d(tag, "onLongClick")
            reset()
            true
        }
    }

    private fun start() {
        Log.d(tag, "start")
        _stopwatch.start()
        _beeperManager.startScheduling { _stopwatch.getElapsedMs() }
        saveInstanceState()
        keepScreenOn()
        setColor(R.color.white)
        display(_stopwatch.getElapsedMs())
        this.logState()
    }

    private fun stop() {
        Log.d(tag, "stop")
        _stopwatch.stop()
        _beeperManager.stopScheduling()
        saveInstanceState()
        unkeepScreenOn()
        setColor(R.color.red)
        val elapsedMs = _stopwatch.getElapsedMs()
        display(elapsedMs) // to show seconds in case of showing only when stopped
        this.logState()
    }

    private fun toggle() {
        Log.d(tag, "toggle")
        if (_stopwatch.isStarted()) {
            stop()
        } else {
            start()
        }
    }

    private fun reset() {
        Log.d(tag, "reset")

        _sharedPreferences.edit {
            putLong(
                "runtime",
                _sharedPreferences.getLong("runtime", 0L) + _stopwatch.getElapsedMs()
            )
        }
        _stopwatch.reset()
        _beeperManager.stopScheduling()
        _beeperManager.reset()
        saveInstanceState()
        unkeepScreenOn()
        setColor(R.color.red)
        setClock(0, 0, 0)
    }

    private fun display(elapsedMs: Long) {
        Log.d(tag, "display")
        if (null === _binding) {
            return
        }
        val elapsed = TimeUnit.MILLISECONDS.toSeconds(elapsedMs)
        Log.d(tag, "${elapsed}s elapsed")

        setClock(
            TimeUnit.SECONDS.toHours(elapsed).toInt(),
            TimeUnit.SECONDS.toMinutes(elapsed).toInt() % 60,
            elapsed.toInt() % 60
        )
    }

    private fun setClock(h: Int, m: Int, s: Int) {
        Log.d(tag, "setClock")
        _binding?.stopwatch?.text = if (_stopwatch.everySecond) getString(R.string.clock_h_mm_ss, h, m, s) else getString(R.string.clock_h_mm, h, m)
    }

    private fun setColor(@ColorRes color: Int) {
        Log.d(tag, "setColor")
        _binding?.stopwatch?.setTextColor(ContextCompat.getColor(requireActivity(), color))
    }

    private fun keepScreenOn() {
        Log.d(tag, "keepScreenOn")
        (requireActivity() as MainActivity).keepScreenOn()
    }

    private fun unkeepScreenOn() {
        Log.d(tag, "unkeepScreenOn")
        (requireActivity() as MainActivity).unkeepScreenOn()
    }

    private fun logState() {
        Log.d(tag, "state={")
        Log.d(tag, "  _startedAt=${_stopwatch.startedAt}")
        Log.d(tag, "  _anteriority=${_stopwatch.anteriority}")
        Log.d(tag, "  _showSeconds=${_stopwatch.everySecond}")
        Log.d(tag, "  isStarted=" + _stopwatch.isStarted().toString())
        Log.d(tag, "}")
    }

    private fun updateVolumeKeyRouting() {
        val pref = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        val overrideSilent = pref.getBoolean(
            getString(R.string.beeper_override_silent_key),
            resources.getBoolean(R.bool.default_beeper_override_silent)
        )

        // BeeperManager uses AudioAttributes.USAGE_ALARM (override) or USAGE_NOTIFICATION (respect silent).
        // Route hardware volume keys to the same stream while this fragment is visible.
        requireActivity().volumeControlStream = if (overrideSilent) {
            AudioManager.STREAM_ALARM
        } else {
            AudioManager.STREAM_NOTIFICATION
        }
    }
}