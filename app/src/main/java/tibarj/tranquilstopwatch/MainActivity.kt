package tibarj.tranquilstopwatch

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.commit
import androidx.preference.PreferenceManager
import tibarj.tranquilstopwatch.databinding.MainActivityBinding


class MainActivity : AppCompatActivity() {

    private val tag: String = "MainActivity"
    private lateinit var binding: MainActivityBinding
    private var _runnable: Runnable? = null
    private val _handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = MainActivityBinding.inflate(layoutInflater)

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
        setContentView(binding.root)

        binding.aboutBtn.setOnClickListener { view ->
            startActivity(Intent(this, AboutActivity::class.java))
        }
        binding.settingsBtn.setOnClickListener { view ->
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        _runnable = Runnable {
            binding.settingsBtn.hide()
            binding.aboutBtn.hide()
        }
        showSettingsButton()
    }

    fun showSettingsButton() {
        _runnable?.let {
            _handler.removeCallbacks(it)
        }
        binding.settingsBtn.show()
        binding.aboutBtn.show()
        _handler.postDelayed(_runnable!!, 5000)
    }
}