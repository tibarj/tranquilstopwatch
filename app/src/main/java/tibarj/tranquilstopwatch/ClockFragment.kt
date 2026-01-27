package tibarj.tranquilstopwatch

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import tibarj.tranquilstopwatch.databinding.ClockFragmentBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ClockFragment : Fragment() {
    private val tag: String = "ClockFragment"
    private var _binding: ClockFragmentBinding? = null
    private var _enabled: Boolean = true
    private lateinit var _clock: Clock

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(tag, "onCreateView")
        _binding = ClockFragmentBinding.inflate(inflater, container, false)
        _clock = Clock (
            _tick = { display() },
        )
        return binding.root
    }

    private fun loadPref() {
        Log.d(tag, "loadPref")

        val pref = PreferenceManager.getDefaultSharedPreferences(requireActivity())

        _enabled = pref.getBoolean(
            getString(R.string.clock_enabled_key),
            resources.getBoolean(R.bool.default_clock_enabled)
        )

        val customTypeface = TimeFontManager.getTypeface(requireContext())
        if (customTypeface != null) {
            Log.d(tag, "setCustomTypeface")
            binding.clock.typeface = customTypeface
        } else {
            val fontFamily = "sans-serif" + if (pref.getBoolean(
                    getString(R.string.clock_font_thin_key),
                    resources.getBoolean(R.bool.default_clock_font_thin)
                )) "-thin" else ""
            Log.d(tag, "setFontFamily $fontFamily")
            binding.clock.typeface = Typeface.create(fontFamily, Typeface.NORMAL)
        }

        val opacity = pref.getInt(
            getString(R.string.clock_opacity_key),
            resources.getInteger(R.integer.default_clock_opacity)
        )
        Log.d(tag, "setClockOpacity $opacity")
        binding.clock.alpha = opacity.toFloat() / 20f

        val size = pref.getInt(getString(R.string.clock_size_key), resources.getInteger(R.integer.default_stopwatch_size))
        Log.d(tag, "setClockSize $size")
        binding.clock.textSize = size.toFloat()

        // Font size changes alter the layout; ask the activity to recenter after relayout.
        binding.root.post {
            (activity as? MainActivity)?.requestRecenter()
        }
    }

    // visible but not interactable
    override fun onStart() {
        Log.d(tag, "onStart")
        super.onStart()

        loadPref()
        logState()

        if (_enabled) {
            display()
            _clock.start()
        }
    }

    override fun onStop() {
        Log.d(tag, "onStop")
        super.onStop()
        _clock.stop()
    }

    private fun display() {
        Log.d(tag, "display")
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val times = LocalDateTime.now().format(formatter).split(':')
        Log.d(tag, times[0]+':'+times[1])
        _binding?.clock?.text = getString(R.string.clock_hh_mm, times[0], times[1])
    }

    private fun logState() {
        Log.d(tag, "state={")
        Log.d(tag, "  _enabled=$_enabled")
        Log.d(tag, "}")
    }
}