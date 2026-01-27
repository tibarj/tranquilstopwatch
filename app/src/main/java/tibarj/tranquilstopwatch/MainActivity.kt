package tibarj.tranquilstopwatch

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import tibarj.tranquilstopwatch.databinding.MainActivityBinding
import kotlin.random.Random


class MainActivity : AppCompatActivity() {

    private val tag: String = "MainActivity"
    private lateinit var _binding: MainActivityBinding
    private var _runnableBtn: Runnable? = null
    private var _runnableMvt: Runnable? = null
    private val _handlerBtn = Handler(Looper.getMainLooper())
    private val _handlerMvt = Handler(Looper.getMainLooper())
    private var _isMvtScheduled = false
    private var _displacement: Int = 0
    private var _randX: Double = 0.0
    private var _randY: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep MainActivity dark even if the system theme is light.
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        Log.d(tag, "onCreate")
        super.onCreate(savedInstanceState)

        _binding = MainActivityBinding.inflate(layoutInflater)

        // apply preferences even if the user hasn't visited the settings activity
        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, true)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // hide bottom bar
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.decorView.setOnApplyWindowInsetsListener { view, windowInsets ->
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            view.onApplyWindowInsets(windowInsets)
        }

        supportActionBar?.hide()
        setContentView(_binding.drawer)

        // Re-center the content when its size changes (e.g. font size updates).
        // Do not re-randomize burn-in offsets for these layout-driven adjustments.
        _binding.content.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val newW = right - left
            val newH = bottom - top
            val oldW = oldRight - oldLeft
            val oldH = oldBottom - oldTop
            if (newW != oldW || newH != oldH) {
                changeMargins(updateRandom = false)
            }
        }

        _runnableBtn = Runnable {
            _binding.menuButton.visibility = View.GONE
        }
        showButtons()
        _binding.main.setOnClickListener {
            showButtons()
        }
        _runnableMvt = Runnable {
            onMvtTimerTick()
        }

        // center the content as soon as the panel is loaded
        _binding.panel.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // Remove the listener to prevent multiple calls
                _binding.panel.viewTreeObserver?.removeOnGlobalLayoutListener(this)

                changeMargins(updateRandom = false)
            }
        })

        initDrawer()
    }

    // visible but not interactable
    override fun onStart() {
        Log.d(tag, "onStart")
        super.onStart()

        loadPref()
        logState()

        if (0 != _displacement) {
            scheduleMvt()
        }
    }

    fun requestRecenter() {
        // Post so this runs after pending layout passes (e.g. after fragments update text size).
        _binding.content.post {
            changeMargins(updateRandom = false)
        }
    }

    override fun onStop() {
        Log.d(tag, "onStop")
        super.onStop()
        if (_isMvtScheduled) {
            unscheduleMvt()
        }
    }

    fun keepScreenOn() {
        Log.d(tag, "keepScreenOn")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun unkeepScreenOn() {
        Log.d(tag, "unkeepScreenOn")
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun initDrawer() {
        Log.d(tag, "initDrawer")
        _binding.menuButton.setOnClickListener {
            if (_binding.drawer.isDrawerOpen(GravityCompat.END)) {
                _binding.drawer.closeDrawer(GravityCompat.END)
            } else {
                _binding.drawer.openDrawer(GravityCompat.END)
            }
        }

        _binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    _binding.drawer.closeDrawer(GravityCompat.END)
                    true
                }
                R.id.nav_beeper -> {
                    startActivity(Intent(this, BeeperActivity::class.java))
                    _binding.drawer.closeDrawer(GravityCompat.END)
                    true
                }
                R.id.nav_about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                    _binding.drawer.closeDrawer(GravityCompat.END)
                    true
                }
                else -> false
            }
        }
    }

    private fun showButtons() {
        Log.d(tag, "showButtons")
        _runnableBtn?.let {
            _handlerBtn.removeCallbacks(it)
        }
        _binding.menuButton.visibility = View.VISIBLE
        val delay = resources.getInteger(R.integer.global_buttons_delay_ms)
        _handlerBtn.postDelayed(_runnableBtn!!, delay.toLong())
    }

    private fun showClock(show: Boolean) {
        _binding.fragmentClock.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showStopwatch(show: Boolean) {
        _binding.fragmentStopwatch.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun scheduleMvt() {
        val delay = resources.getInteger(R.integer.global_displacement_delay_ms)
        Log.d(tag, " >mvt scheduled in " + delay.toString() + "ms")
        _isMvtScheduled = true
        _handlerMvt.postDelayed(_runnableMvt!!, delay.toLong())
    }

    private fun unscheduleMvt() {
        Log.d(tag, "unscheduleMvt")
        _runnableMvt?.let {
            _handlerMvt.removeCallbacks(it)
            _isMvtScheduled = false
        }
    }

    private fun loadPref() {
        Log.d(tag, "loadPref")
        val pref = PreferenceManager.getDefaultSharedPreferences(this)

        val clockEnabled = pref.getBoolean(
            getString(R.string.clock_enabled_key),
            resources.getBoolean(R.bool.default_clock_enabled)
        )
        showClock(clockEnabled)

        val stopwatchEnabled = pref.getBoolean(
            getString(R.string.stopwatch_enabled_key),
            resources.getBoolean(R.bool.default_stopwatch_enabled)
        )
        showStopwatch(stopwatchEnabled)

        // Visibility changes affect content size; re-center without triggering a random move.
        _binding.content.post { changeMargins(updateRandom = false) }

        val displacement = pref.getInt(
            getString(R.string.global_displacement_key),
            resources.getInteger(R.integer.default_global_displacement)
        )
        if (_displacement != displacement) {
            Log.d(tag, "setDisplacement $displacement")
            _displacement = displacement
            changeMargins(updateRandom = false)
        }
    }

    private fun onMvtTimerTick() {
        Log.d(tag, "onMvtTimerTick")
        changeMargins(updateRandom = true)
        scheduleMvt()
    }

    private fun changeMargins(updateRandom: Boolean) {
        Log.d(tag, "changeMargins")

        val hToolbar: Int
        val vToolbar: Int
        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                hToolbar = 0
                vToolbar = _binding.toolbar.height
            }
            else -> {
                hToolbar = _binding.toolbar.width
                vToolbar = 0
            }
        }
        val hPanel = _binding.panel.width
        val vPanel = _binding.panel.height
        val hContent = _binding.content.width
        val vContent = _binding.content.height
        val hSpace = if (0 != hContent) maxOf(0, hPanel - hContent - 2 * hToolbar) else 0
        val vSpace = if (0 != vContent) maxOf(0, vPanel - vContent - 2 * vToolbar) else 0

        // y_max|y_min = (vSpace / 2) * (1 +|- 1)
        // x_max|x_min = (hSpace / 2) * (1 +|- 1)
        val ratio = _displacement.toDouble() /
                (2 * resources.getInteger(R.integer.global_displacement_max).toDouble())
        val hMax = (ratio * hSpace.toDouble()).toInt()
        val vMax = (ratio * vSpace.toDouble()).toInt()

        if (updateRandom) {
            _randX = Random.nextDouble(-1.0, 1.0)
            _randY = Random.nextDouble(-1.0, 1.0)
        }

        val left = (hSpace.toDouble() / 2.0).toInt() + (_randX * hMax.toDouble()).toInt()
        val top = (vSpace.toDouble() / 2.0).toInt() + (_randY * vMax.toDouble()).toInt()

        Log.d(tag, "hSpace $hSpace")
        Log.d(tag, "vSpace $vSpace")
        Log.d(tag, "ratio $ratio")
        Log.d(tag, "hMax $hMax")
        Log.d(tag, "vMax $vMax")
        Log.d(tag, "left $left")
        Log.d(tag, "top $top")
        setMargins(left, top)
    }

    private fun setMargins(h: Int, v: Int) {
        Log.d(tag, "setMargins=($h,$v)")
        val layoutParams = (_binding.content.layoutParams as? MarginLayoutParams)
        layoutParams?.leftMargin = h
        layoutParams?.topMargin = v
        _binding.content.layoutParams = layoutParams
    }

    private fun logState() {
        Log.d(tag, "state={")
        Log.d(tag, "  _displacement=$_displacement")
        Log.d(tag, "}")
    }
}