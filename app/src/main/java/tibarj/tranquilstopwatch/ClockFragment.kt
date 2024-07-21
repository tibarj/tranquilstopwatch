package tibarj.tranquilstopwatch

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import tibarj.tranquilstopwatch.databinding.ClockFragmentBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class ClockFragment : Fragment() {
    private val tag: String = "ClockFragment"
    private var _binding: ClockFragmentBinding? = null
    private var _runnable: Runnable? = null
    private val _handler = Handler(Looper.getMainLooper())
    private var _movement: Int = 0
    private var _showClock: Boolean = true

    // This property is only valid between onCreateView and onDestroyView.
    val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(tag, "onCreateView")
        _binding = ClockFragmentBinding.inflate(inflater, container, false)
        _runnable = Runnable {
            onTimerTick()
        }
        initTapListeners()
        return binding.root
    }

    private fun loadPref() {
        Log.d(tag, "applyPref")

        val pref = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        _showClock = pref.getBoolean(
            getString(R.string.clock_enabled_key),
            resources.getBoolean(R.bool.default_clock_enabled)
        )

        val fontFamily = "sans-serif" + if (pref.getBoolean(
                getString(R.string.clock_font_thin_key),
                resources.getBoolean(R.bool.default_clock_font_thin)
            )) "-thin" else ""
        Log.d(tag, "setFontFamily " + fontFamily)
        binding.clock.typeface = Typeface.create(fontFamily, Typeface.NORMAL)

        val opacity = pref.getInt(
            getString(R.string.clock_opacity_key),
            resources.getInteger(R.integer.default_clock_opacity)
        )
        Log.d(tag, "setClockOpacity " + opacity.toString())
        binding.clock.alpha = opacity.toFloat() / 20f

        val movement = pref.getInt(
            getString(R.string.clock_movement_key),
            resources.getInteger(R.integer.default_clock_movement)
        )
        if (_movement != movement) {
            Log.d(tag, "setClockMovement " + movement.toString())
            _movement = movement
            if (0 == _movement) {
                setMargins(0, 0)
            } else {
                changeMargins()
            }
        }
    }

    // visible but not interactable
    override fun onStart() {
        Log.d(tag, "onStart")
        super.onStart()

        loadPref()
        logState()

        if (_showClock) {
            display()
            schedule()
        }
    }

    override fun onStop() {
        Log.d(tag, "onStop")
        super.onStop()
        if (isScheduled()) {
            unschedule()
        }
    }

    private fun initTapListeners() {
        Log.d(tag, "setTapListeners")
        binding.panel.setOnClickListener {
            Log.d(tag, "OnClickPanel")
            (requireActivity() as MainActivity).showSettingsButton()
        }
    }

    private fun display() {
        Log.d(tag, "display")
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val times = LocalDateTime.now().format(formatter).split(':')
        val s = times[2].toInt()
        Log.d(tag, times[0]+':'+times[1]+':'+times[2])
        _binding?.clock?.text = getString(R.string.clock_hh_mm, times[0], times[1])

        if (s == 0 && 0 != _movement) {
            changeMargins()
        }
    }

    private fun changeMargins() {
        Log.d(tag, "changeMargins")
        val limit = 10 * _movement
        setMargins(Random.nextInt(-limit, limit + 1), 0)
    }

    private fun setMargins(h: Int, v: Int) {
        Log.d(tag, "setMargins")
        val layoutParams = (_binding?.clockParent?.layoutParams as? MarginLayoutParams)
        layoutParams?.setMargins(h, v, -h, -v)
        _binding?.clockParent?.layoutParams = layoutParams
    }

    private fun logState() {
        Log.d(tag, "state={")
        Log.d(tag, "  _showClock=" + _showClock.toString())
        Log.d(tag, "}")
    }

    private fun isScheduled(): Boolean {
        return true == _runnable?.let { _handler.hasCallbacks(it) }
    }

    private fun schedule() {
        Log.d(tag, "schedule")
        // remaining ms time until next minute (10ms of safety)
        val remainingMs = 5000L//60_010 - System.currentTimeMillis() % 60_000
        Log.d(tag, " >scheduled in ${remainingMs}ms")
        _handler.postDelayed(_runnable!!, remainingMs)
    }

    private fun unschedule() {
        Log.d(tag, "unschedule")
        _runnable?.let {
            _handler.removeCallbacks(it)
        }
    }

    private fun onTimerTick() {
        Log.d(tag, "onTimerTick")
        if (null === _binding) {
            return
        }
        display()
        schedule()
    }
}