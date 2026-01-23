package tibarj.tranquilstopwatch

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import org.json.JSONArray
import org.json.JSONObject
import tibarj.tranquilstopwatch.databinding.BeeperActivityBinding
import tibarj.tranquilstopwatch.model.Beeper

class BeeperActivity : AppCompatActivity() {

    private lateinit var binding: BeeperActivityBinding

    /** Adapter that shows the list of beepers */
    private lateinit var beeperAdapter: BeeperAdapter

    /** In‑memory source of truth */
    private val beeperList = mutableListOf<Beeper>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --------------------------------------------------------------------
        // Inflate layout and set up toolbar (up button)
        // --------------------------------------------------------------------
        binding = BeeperActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)               // make toolbar the ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.beeper_title)

        // Handle back button click
        binding.toolbar.setNavigationOnClickListener {
            // Clear focus to trigger any pending onFocusChange listeners
            binding.recyclerBeepers.clearFocus()
            finish()
        }

        loadBeepers()

        // --------------------------------------------------------------------
        // RecyclerView + Adapter
        // --------------------------------------------------------------------
        beeperAdapter = BeeperAdapter(
            onBeeperUpdated = { beeperId, updatedBeeper ->
                val index = beeperList.indexOfFirst { it.id == beeperId }
                if (index != -1) {
                    beeperList[index] = updatedBeeper
                }
            },
            onDeleteConfirmed = { beeperId -> deleteBeeperConfirmed(beeperId) }
        )
        binding.recyclerBeepers.apply {
            layoutManager = LinearLayoutManager(this@BeeperActivity)
            adapter = beeperAdapter
        }

        // Make the list truly usable with the IME (keyboard) open:
        // - increase bottom padding by IME inset so you can scroll to the bottom
        // - avoid auto-scrolling that can prevent scrolling back to the top (toolbar)
        val recycler = binding.recyclerBeepers
        val baseBottomPadding = recycler.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(recycler) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val insetBottom = maxOf(imeBottom, systemBottom)

            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                baseBottomPadding + insetBottom
            )

            insets
        }
        ViewCompat.requestApplyInsets(recycler)
        beeperAdapter.submitList(beeperList.toList())

        // Keep the FABs above the system navigation bar (and keyboard if shown)
        fun applyBottomInsetToFab(fab: android.view.View) {
            val fabLayoutParams = fab.layoutParams as ViewGroup.MarginLayoutParams
            val baseBottomMargin = fabLayoutParams.bottomMargin
            ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
                val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                val insetBottom = maxOf(imeBottom, systemBottom)

                val lp = view.layoutParams as ViewGroup.MarginLayoutParams
                lp.bottomMargin = baseBottomMargin + insetBottom
                view.layoutParams = lp

                insets
            }
            ViewCompat.requestApplyInsets(fab)
        }
        applyBottomInsetToFab(binding.fabAddBeeper)
        applyBottomInsetToFab(binding.fabSaveBeepers)

        // --------------------------------------------------------------------
        // FAB – add a new beeper
        // --------------------------------------------------------------------
        binding.fabAddBeeper.setOnClickListener { addNewBeeper() }

        // --------------------------------------------------------------------
        // FAB – save beepers
        // --------------------------------------------------------------------
        binding.fabSaveBeepers.setOnClickListener {
            // Ensure the currently edited field commits its value before saving.
            currentFocus?.clearFocus()
            binding.recyclerBeepers.clearFocus()
            saveBeepers()
            android.widget.Toast.makeText(this, R.string.beeper_saved, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
    }

    /** Insert a fresh Beeper with default values */
    private fun addNewBeeper() {
        if (beeperList.size >= BeeperManager.MAX_BEEPERS) {
            // Show toast when limit reached
            val max = BeeperManager.MAX_BEEPERS
            val message = resources.getQuantityString(R.plurals.beeper_max_limit, max, max)
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        beeperList.add(Beeper(
            enabled = true,
            frequencyHz = "1000",
            durationMs = "500",
            attenuationDb = "-6",
            afterSec = "0",
            repeatTimes = "0",
            periodicitySec = "60",
            offsetSec = "0",
            waveform = "sine"
        ))
        beeperAdapter.submitList(beeperList.toList()) // ListAdapter needs an immutable copy
    }

    /** Called by the adapter after the user confirms deletion */
    private fun deleteBeeperConfirmed(beeperId: Long) {
        val index = beeperList.indexOfFirst { it.id == beeperId }
        if (index != -1) {
            beeperList.removeAt(index)
            beeperAdapter.submitList(beeperList.toList())
        }
    }

    /**
     * Save beepers to SharedPreferences in JSON format.
     *
     * Storage Format: JSON array where each beeper is an object with fields:
     * - enabled: Boolean - whether beeper is active
     * - frequencyHz: String - tone frequency (10-10000 Hz)
     * - durationMs: String - beep duration (1-999 ms)
     * - attenuationDb: String - volume attenuation (-100.0 to 0.0 dB)
     * - afterSec: String - initial delay before first beep (0-86400 s)
     * - repeatTimes: String - number of repetitions (0=infinite, max 1000000)
     * - periodicitySec: String - time between beeps (60-86400 s)
     * - waveform: String - wave shape ("sine", "square", "triangle", "sawtooth")
     *
     * Stored under key: "beepers_json"
     * Old format ("beepers" StringSet) maintained for backward compatibility.
     */
    private fun saveBeepers() {
        val prefs = getSharedPreferences("BeeperActivity", Context.MODE_PRIVATE)

        // Save as JSON for predictable ordering
        val jsonArray = JSONArray()
        beeperList.forEach { beeper ->
            val json = JSONObject()
            json.put("id", beeper.id)
            json.put("enabled", beeper.enabled)
            json.put("frequencyHz", beeper.frequencyHz)
            json.put("durationMs", beeper.durationMs)
            json.put("attenuationDb", beeper.attenuationDb)
            json.put("afterSec", beeper.afterSec)
            json.put("repeatTimes", beeper.repeatTimes)
            json.put("periodicitySec", beeper.periodicitySec)
            json.put("offsetSec", beeper.offsetSec)
            json.put("waveform", beeper.waveform)
            jsonArray.put(json)
        }

        prefs.edit {
            putString("beepers_json", jsonArray.toString())
        }
    }

    /**
     * Load beepers from SharedPreferences with backward compatibility.
     *
     * Primary Format ("beepers_json"): JSON array of beeper objects
     *
     * Fallback Format ("beepers"): StringSet with CSV entries:
     * Format: "enabled,frequencyHz,durationMs,attenuationDb,afterSec,repeatTimes[,periodicitySec][,waveform]"
     * - 6 fields: Original format (periodicitySec defaults to afterSec)
     * - 7 fields: Added periodicitySec field
     * - 8 fields: Current format with waveform (defaults to "sine" if missing)
     *
     * Migration: On first save after loading CSV format, data is migrated to JSON.
     */
    private fun loadBeepers() {
        val prefs = getSharedPreferences("BeeperActivity", Context.MODE_PRIVATE)
        beeperList.clear()

        // Try new JSON format first
        val jsonString = prefs.getString("beepers_json", null)
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    beeperList.add(Beeper(
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
                android.util.Log.e("BeeperActivity", "Error parsing JSON: ${e.message}")
            }
        } else {
            // Fallback to old StringSet format for backward compatibility
            // CSV Format: "enabled,frequencyHz,durationMs,attenuationDb,afterSec,repeatTimes,periodicitySec,waveform"
            // Supports 6-8 fields for migration from older app versions
            val beeperStrings = prefs.getStringSet("beepers", emptySet())
            beeperStrings?.forEach { str ->
                val parts = str.split(",")
                if (parts.size >= 6) {
                    beeperList.add(Beeper(
                        enabled = parts[0].toBoolean(),
                        frequencyHz = parts[1],
                        durationMs = parts[2],
                        attenuationDb = parts[3],
                        afterSec = parts[4],
                        repeatTimes = parts[5],
                        periodicitySec = if (parts.size >= 7) parts[6] else parts[4],
                        waveform = if (parts.size >= 8) parts[7] else "sine",
                        id = Beeper.nextId()
                    ))
                }
            }
        }
    }
}
