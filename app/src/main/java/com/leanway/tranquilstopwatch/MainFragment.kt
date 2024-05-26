package com.leanway.tranquilstopwatch

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.leanway.tranquilstopwatch.databinding.MainFragmentBinding
import java.util.concurrent.TimeUnit
import kotlin.random.Random


class MainFragment : Fragment() {
    private val tag: String = "MainFragment"
    private lateinit var _sharedPreferences: SharedPreferences
    private var _binding: MainFragmentBinding? = null
    private var _runnable: Runnable? = null
    private val _handler = Handler(Looper.getMainLooper())
    private var _startedAt: Long = 0 // ms
    private var _anteriority: Long = 0 // ms, sum of all start-stop segments durations
    private var _showSeconds: Boolean = true
    private var _showSecondsWhenStarted: Boolean = true
    private var _movement: Int = 0

    // This property is only valid between onCreateView and onDestroyView.
    val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(tag, "onCreateView")
        _sharedPreferences = requireContext().getSharedPreferences(
            "appState",
            Context.MODE_PRIVATE
        )
        _startedAt = _sharedPreferences.getLong("startedAt", 0L)
        _anteriority = _sharedPreferences.getLong("anteriority", 0L)
        _binding = MainFragmentBinding.inflate(inflater, container, false)
        _runnable = Runnable {
            onTimerTick()
        }
        initTapListeners()
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate")
    }

    override fun onResume() {
        super.onResume()
        Log.d(tag, "onResume")
    }

    fun saveInstanceState() {
        Log.d(tag, "saveInstanceState")

        with(_sharedPreferences.edit()) {
            putLong("startedAt", _startedAt)
            putLong("anteriority", _anteriority)
            apply()
        }
        logState()
    }

    private fun loadPref() {
        Log.d(tag, "applyPref")
        val pref = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        _showSeconds = pref.getBoolean(
            getString(R.string.show_seconds_key),
            resources.getBoolean(R.bool.default_show_seconds)
        )
        _showSecondsWhenStarted = _showSeconds && !pref.getBoolean(
            getString(R.string.show_seconds_only_when_stopped_key),
            resources.getBoolean(R.bool.default_show_seconds_only_when_stopped)
        )

        val opacity = pref.getInt(getString(R.string.clock_opacity_key), resources.getInteger(R.integer.default_clock_opacity))
        Log.d(tag, "setClockOpacity " + opacity.toString())
        binding.timeview.alpha = opacity.toFloat() / 20f

        val size = pref.getInt("clock_size_key", resources.getInteger(R.integer.default_clock_size))
        Log.d(tag, "setClockSize " + size.toString())
        binding.timeview.textSize = size.toFloat()

        val movement = pref.getInt("clock_movement_key", resources.getInteger(R.integer.default_clock_movement))
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

        display()
        if (isStarted()) {
            keepScreenOn()
            setColor(R.color.white)
            schedule()
        } else {
            setColor(R.color.red)
        }
    }

    override fun onStop() {
        Log.d(tag, "onStop")
        super.onStop()

        unschedule()
        unkeepScreenOn()
    }

    private fun initTapListeners() {
        Log.d(tag, "setTapListeners")
        binding.timeview.setOnClickListener {
            Log.d(tag, "OnClickTimeview")
            toggle()
        }
        binding.timeview.setOnLongClickListener {
            Log.d(tag, "onLongClickTimeview")
            reset()
            true
        }
        binding.panel.setOnClickListener {
            Log.d(tag, "OnClickPanel")
            (requireActivity() as MainActivity).showSettingsButton()
        }
    }

    private fun isStarted(): Boolean {
        return 0L != _startedAt
    }

    private fun start() {
        Log.d(tag, "start")
        _startedAt = System.currentTimeMillis()
        saveInstanceState()
        keepScreenOn()
        setColor(R.color.white)
        if (_showSeconds && !_showSecondsWhenStarted) {
            display() // hide seconds because showing only when stopped
        }
        schedule()
        this.logState()
    }

    private fun stop() {
        Log.d(tag, "stop")
        unschedule()
        _anteriority += System.currentTimeMillis() - _startedAt
        _startedAt = 0L
        saveInstanceState()
        unkeepScreenOn()
        setColor(R.color.red)
        if (_showSeconds && !_showSecondsWhenStarted) {
            display() // show seconds because showing only when stopped
        }
        this.logState()
    }

    private fun toggle() {
        Log.d(tag, "toggle")
        if (isStarted()) {
            stop()
        } else {
            start()
        }
    }

    private fun reset() {
        Log.d(tag, "reset")
        unschedule()
        _startedAt = 0L
        _anteriority = 0L
        saveInstanceState()
        unkeepScreenOn()
        setColor(R.color.red)
        _binding?.timeview?.setText(
            if (_showSeconds) R.string.default_second_time
            else R.string.default_minute_time
        )
    }

    private fun display() {
        Log.d(tag, "display")
        val elapsed = _anteriority + if (isStarted()) System.currentTimeMillis() - _startedAt else 0L
        Log.d(tag, " >elapsed=$elapsed")
        val h = TimeUnit.MILLISECONDS.toHours(elapsed)
        val m = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60
        var s = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
        _binding?.timeview?.text =
            // hours
            (if (h != 0L) String.format("%d:", h) else "") +
            // minutes
            String.format("%02d", m) +
            // seconds
            if (_showSecondsWhenStarted || (!isStarted() && _showSeconds))
                String.format(":%02d", s) else ""
        if (s == 0L && 0 != _movement) {
            changeMargins()
        }
    }

    private fun changeMargins() {
        Log.d(tag, "changeMargins")
        val limit = 10 * _movement
        setMargins(Random.nextInt(-limit, limit + 1), Random.nextInt(-limit, limit + 1))
    }

    private fun setMargins(h: Int, v: Int) {
        Log.d(tag, "setMargins")
        val layoutParams = (_binding?.timeview?.layoutParams as? MarginLayoutParams)
        layoutParams?.setMargins(h, v, -h, -v)
        _binding?.timeview?.layoutParams = layoutParams
    }

    private fun setColor(@ColorRes color: Int) {
        Log.d(tag, "setColor")
        _binding?.timeview?.setTextColor(ContextCompat.getColor(requireActivity(), color))
    }

    private fun keepScreenOn() {
        Log.d(tag, "keepScreenOn")
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun unkeepScreenOn() {
        Log.d(tag, "unkeepScreenOn")
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun logState() {
        Log.d(tag, "state={")
        Log.d(tag, "  _startedAt=" + _startedAt.toString())
        Log.d(tag, "  _anteriority=" + _anteriority.toString())
        Log.d(tag, "  _showSeconds=" + _showSeconds.toString())
        Log.d(tag, "  _showSecondsWhenStarted=" + _showSecondsWhenStarted.toString())
        Log.d(tag, "  isStarted=" + isStarted().toString())
        Log.d(tag, "}")
    }

//    private fun isScheduled(): Boolean {
//        return true == _runnable?.let { _handler.hasCallbacks(it) }
//    }

    private fun schedule() {
        Log.d(tag, "schedule")
        val elapsed = (_anteriority + System.currentTimeMillis() - _startedAt)
        // remaining ms time until next minute (10ms of safety)
        val remainingMs = if (_showSecondsWhenStarted) 1_010 - elapsed % 1_000
            else 60_010 - elapsed % 60_000
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