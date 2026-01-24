package tibarj.tranquilstopwatch

import android.widget.Toast
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val openFontPicker = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) return@registerForActivityResult

            val result = TimeFontManager.importFromUri(requireContext(), uri)
            if (result.isSuccess) {
                Toast.makeText(requireContext(), getString(R.string.time_font_imported_toast), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.time_font_import_failed_toast, result.exceptionOrNull()?.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }

            updateTimeFontPreferences()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            findPreference<Preference>(getString(R.string.time_font_import_key))
                ?.setOnPreferenceClickListener {
                    openFontPicker.launch(
                        arrayOf(
                            "font/ttf",
                            "application/x-font-ttf"
                        )
                    )
                    true
                }

            findPreference<Preference>(getString(R.string.time_font_reset_key))
                ?.setOnPreferenceClickListener {
                    TimeFontManager.clear(requireContext())
                    updateTimeFontPreferences()
                    true
                }

            updateTimeFontPreferences()
        }

        private fun updateTimeFontPreferences() {
            val name = TimeFontManager.getDisplayName(requireContext())
            val hasFont = TimeFontManager.hasCustomFont(requireContext())

            val importPref = findPreference<Preference>(getString(R.string.time_font_import_key))
            val hint = getString(R.string.time_font_pick_hint)
            importPref?.summary = if (hasFont) {
                hint + "\n" + getString(
                    R.string.time_font_current_summary_custom,
                    name ?: getString(R.string.time_font_unknown_name)
                )
            } else {
                hint + "\n" + getString(R.string.time_font_current_summary_default)
            }

            val resetPref = findPreference<Preference>(getString(R.string.time_font_reset_key))
            resetPref?.isVisible = hasFont

            // The "thin" toggle only affects the built-in sans-serif family.
            // Keep it visible but disabled with an explanation when a custom font is active.
            val thinDisabledSummary = getString(R.string.time_font_thin_disabled_summary)

            findPreference<Preference>(getString(R.string.clock_font_thin_key))?.apply {
                isEnabled = !hasFont
                summary = if (hasFont) thinDisabledSummary else null
            }
            findPreference<Preference>(getString(R.string.stopwatch_font_thin_key))?.apply {
                isEnabled = !hasFont
                summary = if (hasFont) thinDisabledSummary else null
            }
        }
    }
}