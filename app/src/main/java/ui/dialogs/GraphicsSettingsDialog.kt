package ui.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Typeface
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.libopenmw.openmw.R
import file.GraphicsPresets

object GraphicsSettingsDialog {
    private val masterValues = arrayOf("auto", "low", "medium", "high", "ultra", "custom")
    private val detailValues = arrayOf("low", "medium", "high", "ultra")

    fun show(activity: Activity, prefs: SharedPreferences) {
        GraphicsPresets.ensureAutoInitialized(activity, prefs)

        val density = activity.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density + 0.5f).toInt()

        val scroll = ScrollView(activity)
        val root = LinearLayout(activity)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(10), dp(20), dp(14))
        scroll.addView(root)

        val intro = TextView(activity)
        intro.setText(R.string.pref_graphics_dialog_intro)
        intro.setPadding(0, 0, 0, dp(10))
        root.addView(intro)

        fun addLabel(text: String, bold: Boolean = false): TextView {
            val label = TextView(activity)
            label.text = text
            if (bold)
                label.setTypeface(label.typeface, Typeface.BOLD)
            label.setPadding(0, dp(8), 0, dp(4))
            root.addView(label)
            return label
        }

        fun makeSpinner(entries: Array<String>): Spinner {
            val spinner = Spinner(activity)
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, entries)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            root.addView(spinner, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            return spinner
        }

        addLabel(activity.getString(R.string.pref_graphics_master), true)
        val masterEntries = activity.resources.getStringArray(R.array.pref_graphics_master_entries)
        val masterSpinner = makeSpinner(masterEntries)

        val autoHint = TextView(activity)
        val recommended = GraphicsPresets.recommendLevel(activity)
        autoHint.text = activity.getString(R.string.pref_graphics_auto_detected, levelLabel(activity, recommended))
        autoHint.setPadding(0, dp(2), 0, dp(8))
        root.addView(autoHint)

        val detailSpecs = arrayOf(
            Triple(GraphicsPresets.OSG_KEY, R.string.pref_graphics_osg, R.string.pref_graphics_osg_hint),
            Triple(GraphicsPresets.STREAMING_KEY, R.string.pref_graphics_streaming, R.string.pref_graphics_streaming_hint),
            Triple(GraphicsPresets.TERRAIN_KEY, R.string.pref_graphics_terrain, R.string.pref_graphics_terrain_hint),
            Triple(GraphicsPresets.SHADERS_KEY, R.string.pref_graphics_shaders, R.string.pref_graphics_shaders_hint),
            Triple(GraphicsPresets.LIGHTING_KEY, R.string.pref_graphics_lighting, R.string.pref_graphics_lighting_hint),
            Triple(GraphicsPresets.SHADOWS_KEY, R.string.pref_graphics_shadows, R.string.pref_graphics_shadows_hint),
            Triple(GraphicsPresets.GRASS_KEY, R.string.pref_graphics_grass, R.string.pref_graphics_grass_hint)
        )

        val detailEntries = activity.resources.getStringArray(R.array.pref_graphics_detail_entries)
        val detailSpinners = ArrayList<Pair<String, Spinner>>()
        for (spec in detailSpecs) {
            addLabel(activity.getString(spec.second), true)
            val spinner = makeSpinner(detailEntries)
            val hint = TextView(activity)
            hint.setText(spec.third)
            hint.setPadding(0, 0, 0, dp(4))
            root.addView(hint)
            detailSpinners.add(Pair(spec.first, spinner))
        }

        var internalChange = true
        val masterStored = prefs.getString(GraphicsPresets.MASTER_KEY, "auto") ?: "auto"
        masterSpinner.setSelection(indexOf(masterValues, masterStored, 0), false)
        for (pair in detailSpinners) {
            val stored = GraphicsPresets.normalizeLevel(prefs.getString(pair.first, "medium"))
            pair.second.setSelection(indexOf(detailValues, stored, 1), false)
        }
        internalChange = false

        fun setDetails(level: String) {
            val idx = indexOf(detailValues, GraphicsPresets.normalizeLevel(level), 1)
            internalChange = true
            for (pair in detailSpinners)
                pair.second.setSelection(idx)
            internalChange = false
        }

        masterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (internalChange)
                    return
                when (masterValues[position]) {
                    "auto" -> setDetails(GraphicsPresets.recommendLevel(activity))
                    "low", "medium", "high", "ultra" -> setDetails(masterValues[position])
                }
            }
        }

        val detailListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (internalChange)
                    return

                // Spinner callbacks may arrive after a programmatic setSelection().
                // If every detail still matches the selected master profile, this is
                // not a user override and must not flip the master selector to Custom.
                val master = masterValues[masterSpinner.selectedItemPosition]
                val expected = when (master) {
                    "auto" -> GraphicsPresets.recommendLevel(activity)
                    "low", "medium", "high", "ultra" -> master
                    else -> null
                }
                if (expected != null) {
                    var allMatch = true
                    for (pair in detailSpinners) {
                        if (detailValues[pair.second.selectedItemPosition] != expected) {
                            allMatch = false
                            break
                        }
                    }
                    if (allMatch)
                        return
                }

                if (master != "custom") {
                    internalChange = true
                    masterSpinner.setSelection(indexOf(masterValues, "custom", 5))
                    internalChange = false
                }
            }
        }
        for (pair in detailSpinners)
            pair.second.onItemSelectedListener = detailListener

        AlertDialog.Builder(activity)
            .setTitle(R.string.pref_graphics_button)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val master = masterValues[masterSpinner.selectedItemPosition]
                val edit = prefs.edit()
                edit.putString(GraphicsPresets.MASTER_KEY, master)
                for (pair in detailSpinners) {
                    val level = detailValues[pair.second.selectedItemPosition]
                    edit.putString(pair.first, level)
                }
                if (master == "auto") {
                    val autoLevel = detailValues[detailSpinners[0].second.selectedItemPosition]
                    edit.putString(GraphicsPresets.AUTO_LEVEL_KEY, autoLevel)
                    edit.putBoolean(GraphicsPresets.AUTO_INITIALIZED_KEY, true)
                }
                edit.apply()
            }
            .show()
    }

    private fun indexOf(values: Array<String>, value: String, fallback: Int): Int {
        for (i in values.indices) {
            if (values[i] == value)
                return i
        }
        return fallback
    }

    private fun levelLabel(activity: Activity, level: String): String {
        return when (GraphicsPresets.normalizeLevel(level)) {
            "low" -> activity.getString(R.string.pref_graphics_level_low)
            "high" -> activity.getString(R.string.pref_graphics_level_high)
            "ultra" -> activity.getString(R.string.pref_graphics_level_ultra)
            else -> activity.getString(R.string.pref_graphics_level_medium)
        }
    }
}
