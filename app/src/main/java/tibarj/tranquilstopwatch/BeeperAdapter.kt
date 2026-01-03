package tibarj.tranquilstopwatch

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import tibarj.tranquilstopwatch.databinding.BeeperItemBinding
import tibarj.tranquilstopwatch.model.Beeper

/**
 * Adapter for the vertical list of beepers.
 * Handles:
 * • toggling enabled/disabled (green/red thumb)
 * • editing the numeric fields
 * • a two‑step delete button (first click → red, second click → remove)
 */
class BeeperAdapter(
    private val onBeeperUpdated: (beeperId: Long, beeper: Beeper) -> Unit,
    private val onDeleteConfirmed: (beeperId: Long) -> Unit
) : ListAdapter<Beeper, BeeperAdapter.BeeperViewHolder>(BeeperDiffCallback()) {

    /** Keeps track of which rows are awaiting the second‑click confirmation */
    private val pendingDeleteIds = mutableSetOf<Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BeeperViewHolder {
        val binding = BeeperItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BeeperViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BeeperViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    /** ViewHolder that binds a single Beeper instance to the row layout */
    inner class BeeperViewHolder(private val b: BeeperItemBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(beeper: Beeper, pos: Int) {
            val beeperId = beeper.id
            // ----- Enabled toggle -------------------------------------------------
            b.switchEnabled.isChecked = beeper.enabled
            updateToggleColor(b.switchEnabled, beeper.enabled)

            b.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onBeeperUpdated(beeperId, beeper.copy(enabled = isChecked))
                updateToggleColor(b.switchEnabled, isChecked)
            }

            // Helper to avoid infinite loops when we set text programmatically
            fun setIfChanged(edit: TextInputEditText, value: String) {
                if (edit.text?.toString() != value) edit.setText(value)
            }

            // Hybrid commit: pressing IME "Done" commits/clamps immediately
            // (by clearing focus, which triggers the existing onFocusChange logic).
            fun installImeDoneCommit(edit: TextInputEditText) {
                edit.setOnEditorActionListener { v, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        v.clearFocus()
                        b.root.requestFocus()
                        true
                    } else {
                        false
                    }
                }
            }

            setIfChanged(b.inputFrequency, beeper.frequencyHz)
            setIfChanged(b.inputDuration, beeper.durationMs)
            setIfChanged(b.inputGain, beeper.attenuationDb)
            setIfChanged(b.inputOffset, beeper.offsetSec)
            setIfChanged(b.inputAfter, beeper.afterSec)
            setIfChanged(b.inputTimes, beeper.repeatTimes)
            setIfChanged(b.inputPeriod, beeper.periodicitySec)

            installImeDoneCommit(b.inputFrequency)
            installImeDoneCommit(b.inputDuration)
            installImeDoneCommit(b.inputGain)
            installImeDoneCommit(b.inputOffset)
            installImeDoneCommit(b.inputAfter)
            installImeDoneCommit(b.inputTimes)
            installImeDoneCommit(b.inputPeriod)

            // ----- Waveform spinner -----------------------------------------------
            val waveforms = listOf("sine", "square", "triangle", "sawtooth")
            val waveformLabels = listOf(
                b.root.context.getString(R.string.beeper_waveform_sine),
                b.root.context.getString(R.string.beeper_waveform_square),
                b.root.context.getString(R.string.beeper_waveform_triangle),
                b.root.context.getString(R.string.beeper_waveform_sawtooth)
            )
            val adapter = android.widget.ArrayAdapter(
                b.root.context,
                android.R.layout.simple_spinner_item,
                waveformLabels
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            b.spinnerWaveform.adapter = adapter

            val currentIndex = waveforms.indexOf(beeper.waveform).coerceAtLeast(0)
            b.spinnerWaveform.setSelection(currentIndex)

            b.spinnerWaveform.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, waveformPos: Int, id: Long) {
                    onBeeperUpdated(beeperId, beeper.copy(waveform = waveforms[waveformPos]))
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

            // Update model when user edits, clamping to valid ranges
            b.inputFrequency.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val value = b.inputFrequency.text?.toString()?.toIntOrNull() ?: 1000
                    val clamped = value.coerceIn(Beeper.MIN_FREQUENCY_HZ, Beeper.MAX_FREQUENCY_HZ)
                    onBeeperUpdated(beeperId, beeper.copy(frequencyHz = clamped.toString()))
                    setIfChanged(b.inputFrequency, clamped.toString())
                }
            }
            b.inputDuration.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val value = b.inputDuration.text?.toString()?.toIntOrNull() ?: 500
                    val clamped = value.coerceIn(Beeper.MIN_DURATION_MS, Beeper.MAX_DURATION_MS)
                    onBeeperUpdated(beeperId, beeper.copy(durationMs = clamped.toString()))
                    setIfChanged(b.inputDuration, clamped.toString())
                }
            }
            b.inputGain.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val value = b.inputGain.text?.toString()?.toIntOrNull() ?: -6
                    val clamped = value.coerceIn(Beeper.MIN_ATTENUATION_DB, Beeper.MAX_ATTENUATION_DB)
                    onBeeperUpdated(beeperId, beeper.copy(attenuationDb = clamped.toString()))
                    setIfChanged(b.inputGain, clamped.toString())
                }
            }
            b.inputOffset.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val value = b.inputOffset.text?.toString()?.toLongOrNull() ?: 0L
                    val clamped = value.coerceIn(Beeper.MIN_OFFSET_SEC, Beeper.MAX_OFFSET_SEC)
                    onBeeperUpdated(beeperId, beeper.copy(offsetSec = clamped.toString()))
                    setIfChanged(b.inputOffset, clamped.toString())
                }
            }
            b.inputAfter.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val value = b.inputAfter.text?.toString()?.toLongOrNull() ?: 0L
                    val clamped = value.coerceIn(Beeper.MIN_AFTER_SEC, Beeper.MAX_AFTER_SEC)
                    onBeeperUpdated(beeperId, beeper.copy(afterSec = clamped.toString()))
                    setIfChanged(b.inputAfter, clamped.toString())
                }
            }
            b.inputTimes.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val value = b.inputTimes.text?.toString()?.toIntOrNull() ?: 0
                    val clamped = value.coerceIn(Beeper.MIN_REPEAT_TIMES, Beeper.MAX_REPEAT_TIMES)
                    onBeeperUpdated(beeperId, beeper.copy(repeatTimes = clamped.toString()))
                    setIfChanged(b.inputTimes, clamped.toString())
                }
            }
            b.inputPeriod.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val value = b.inputPeriod.text?.toString()?.toLongOrNull() ?: 60L
                    val clamped = value.coerceIn(Beeper.MIN_PERIODICITY_SEC, Beeper.MAX_PERIODICITY_SEC)
                    onBeeperUpdated(beeperId, beeper.copy(periodicitySec = clamped.toString()))
                    setIfChanged(b.inputPeriod, clamped.toString())
                }
            }

            // ----- Help icon click listeners --------------------------------------
            b.inputPeriodLayout.setEndIconOnClickListener {
                showHelpDialog(b.root.context.getString(R.string.beeper_help_period))
            }
            b.inputTimesLayout.setEndIconOnClickListener {
                showHelpDialog(b.root.context.getString(R.string.beeper_help_repeat))
            }
            b.inputFrequencyLayout.setEndIconOnClickListener {
                showHelpDialog(b.root.context.getString(R.string.beeper_help_frequency))
            }
            b.inputDurationLayout.setEndIconOnClickListener {
                showHelpDialog(b.root.context.getString(R.string.beeper_help_duration))
            }
            b.inputOffsetLayout.setEndIconOnClickListener {
                showHelpDialog(b.root.context.getString(R.string.beeper_help_offset))
            }
            b.inputAfterLayout.setEndIconOnClickListener {
                showHelpDialog(b.root.context.getString(R.string.beeper_help_after))
            }
            b.inputGainLayout.setEndIconOnClickListener {
                showHelpDialog(b.root.context.getString(R.string.beeper_help_attenuation))
            }

            // ----- Delete button --------------------------------------------------
            val isPending = pendingDeleteIds.contains(beeperId)
            b.btnDelete.text = if (isPending) {
                b.root.context.getString(R.string.beeper_confirm_delete)
            } else {
                b.root.context.getString(R.string.beeper_delete)
            }
            val errorColor = com.google.android.material.R.attr.colorError
            val errorColorValue = com.google.android.material.color.MaterialColors.getColor(
                b.root.context, errorColor, android.graphics.Color.RED
            )
            b.btnDelete.setBackgroundColor(
                if (isPending) errorColorValue else com.google.android.material.color.MaterialColors.getColor(
                    b.root.context, com.google.android.material.R.attr.colorErrorContainer, errorColorValue
                )
            )
            // Set text color to ensure visibility on colored background
            val textColor = if (isPending) {
                // White text on bright error color
                android.graphics.Color.WHITE
            } else {
                // Use onErrorContainer for proper contrast
                com.google.android.material.color.MaterialColors.getColor(
                    b.root.context, com.google.android.material.R.attr.colorOnErrorContainer, android.graphics.Color.WHITE
                )
            }
            b.btnDelete.setTextColor(textColor)

            b.btnDelete.setOnClickListener {
                if (isPending) {
                    // second click → actually delete
                    pendingDeleteIds.remove(beeperId)
                    onDeleteConfirmed(beeperId)
                } else {
                    // first click → mark for confirmation
                    pendingDeleteIds.add(beeperId)
                    // refresh only this item to show the new button state
                    notifyItemChanged(pos)
                }
            }
        }

        /** Shows a help dialog with the given message */
        private fun showHelpDialog(message: String) {
            androidx.appcompat.app.AlertDialog.Builder(b.root.context)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        /** Sets the thumb colour of the SwitchMaterial: green when enabled, red when disabled */
        private fun updateToggleColor(switch: SwitchMaterial, enabled: Boolean) {
            val color = if (enabled) {
                com.google.android.material.color.MaterialColors.getColor(
                    switch.context, com.google.android.material.R.attr.colorPrimary, android.graphics.Color.GREEN
                )
            } else {
                com.google.android.material.color.MaterialColors.getColor(
                    switch.context, com.google.android.material.R.attr.colorError, android.graphics.Color.RED
                )
            }
            switch.thumbTintList = android.content.res.ColorStateList.valueOf(color)
        }
    }
}

/** DiffUtil implementation for efficient list updates */
private class BeeperDiffCallback : DiffUtil.ItemCallback<Beeper>() {
    override fun areItemsTheSame(oldItem: Beeper, newItem: Beeper): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Beeper, newItem: Beeper): Boolean {
        return oldItem == newItem
    }
}
