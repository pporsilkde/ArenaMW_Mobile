/*
    Copyright (C) 2016 sandstranger
    Copyright (C) 2018, 2019 Ilya Zhuravlev

    This file is part of OpenMW-Android.

    OpenMW-Android is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    OpenMW-Android is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with OpenMW-Android.  If not, see <https://www.gnu.org/licenses/>.
*/

package ui.fragments

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Typeface
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import androidx.core.content.ContextCompat

import com.codekidlabs.storagechooser.StorageChooser
import com.libopenmw.openmw.R
import file.GameInstaller
import file.GraphicsPresets
import file.BuildManifestManager

import ui.activity.ConfigureControls
import ui.activity.MainActivity
import ui.activity.ModsActivity
import utils.MyApp
import java.util.*

class FragmentSettings : PreferenceFragment(), OnSharedPreferenceChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.settings)
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        updateGammaState()

        findPreference("pref_controls").setOnPreferenceClickListener {
            val intent = Intent(activity, ConfigureControls::class.java)
            this.startActivity(intent)
            true
        }

        findPreference("pref_mods").setOnPreferenceClickListener {
            val intent = Intent(activity, ModsActivity::class.java)
            this.startActivity(intent)
            true
        }

        findPreference("pref_graphics_settings").setOnPreferenceClickListener {
            showGraphicsSettingsDialog()
            true
        }

        findPreference("game_files").setOnPreferenceClickListener {
            if (ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                showError(R.string.permissions_error_title, R.string.permissions_error_message)
            } else {
                val chooser = StorageChooser.Builder()
                    .withActivity(activity)
                    .withFragmentManager(fragmentManager)
                    .withMemoryBar(true)
                    .allowCustomPath(true)
                    .setType(StorageChooser.DIRECTORY_CHOOSER)
                    .build()

                chooser.show()

                chooser.setOnSelectListener { path -> setupData(path) }
            }
            true
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun graphicsLevelLabels(): Array<String> = arrayOf(
        getString(R.string.graphics_level_low),
        getString(R.string.graphics_level_medium),
        getString(R.string.graphics_level_high),
        getString(R.string.graphics_level_ultra)
    )

    private fun overallLabels(): Array<String> = arrayOf(
        getString(R.string.graphics_level_low),
        getString(R.string.graphics_level_medium),
        getString(R.string.graphics_level_high),
        getString(R.string.graphics_level_ultra),
        getString(R.string.graphics_level_custom)
    )

    private fun overallValues(): Array<String> = arrayOf(
        GraphicsPresets.LOW,
        GraphicsPresets.MEDIUM,
        GraphicsPresets.HIGH,
        GraphicsPresets.ULTRA,
        GraphicsPresets.CUSTOM
    )

    private fun levelLabel(level: String): String {
        return when (level) {
            GraphicsPresets.LOW -> getString(R.string.graphics_level_low)
            GraphicsPresets.HIGH -> getString(R.string.graphics_level_high)
            GraphicsPresets.ULTRA -> getString(R.string.graphics_level_ultra)
            GraphicsPresets.CUSTOM -> getString(R.string.graphics_level_custom)
            else -> getString(R.string.graphics_level_medium)
        }
    }

    private fun updateGraphicsPreferenceSummary() {
        val pref = findPreference("pref_graphics_settings") ?: return
        val shared = preferenceScreen.sharedPreferences
        GraphicsPresets.ensureInitialized(activity, shared)
        val overall = GraphicsPresets.normalizeOverall(
            shared.getString(GraphicsPresets.PREF_OVERALL, GraphicsPresets.MEDIUM)
        )
        pref.summary = getString(R.string.graphics_profile_summary_format, levelLabel(overall))
    }

    private fun showGraphicsSettingsDialog() {
        val shared = preferenceScreen.sharedPreferences
        GraphicsPresets.ensureInitialized(activity, shared)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(8))
        }
        val scroll = ScrollView(activity).apply { addView(root) }

        fun addSelector(titleRes: Int, labels: Array<String>): Spinner {
            val title = TextView(activity).apply {
                setText(titleRes)
                textSize = 14f
                setPadding(0, dp(10), 0, dp(4))
            }
            root.addView(title, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            val spinner = Spinner(activity)
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, labels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            root.addView(spinner, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            return spinner
        }

        val overallTitle = TextView(activity).apply {
            setText(R.string.graphics_overall_profile)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 15f
            setPadding(0, dp(6), 0, dp(4))
        }
        root.addView(overallTitle)

        val overallSpinner = Spinner(activity)
        val overallAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, overallLabels())
        overallAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        overallSpinner.adapter = overallAdapter
        root.addView(overallSpinner, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val levelLabels = graphicsLevelLabels()
        val osgSpinner = addSelector(R.string.graphics_osg_profile, levelLabels)
        val streamingSpinner = addSelector(R.string.graphics_streaming_profile, levelLabels)
        val terrainSpinner = addSelector(R.string.graphics_terrain_profile, levelLabels)
        val shadersSpinner = addSelector(R.string.graphics_shaders_profile, levelLabels)
        val lightingSpinner = addSelector(R.string.graphics_lighting_profile, levelLabels)
        val shadowsSpinner = addSelector(R.string.graphics_shadows_profile, levelLabels)
        val grassSpinner = addSelector(R.string.graphics_grass_profile, levelLabels)

        val note = TextView(activity).apply {
            setText(R.string.graphics_shadow_safety_note)
            textSize = 12f
            setPadding(0, dp(16), 0, dp(8))
        }
        root.addView(note)

        val groupSpinners = arrayOf(
            osgSpinner, streamingSpinner, terrainSpinner, shadersSpinner,
            lightingSpinner, shadowsSpinner, grassSpinner
        )
        val groupKeys = arrayOf(
            GraphicsPresets.PREF_OSG,
            GraphicsPresets.PREF_STREAMING,
            GraphicsPresets.PREF_TERRAIN,
            GraphicsPresets.PREF_SHADERS,
            GraphicsPresets.PREF_LIGHTING,
            GraphicsPresets.PREF_SHADOWS,
            GraphicsPresets.PREF_GRASS
        )

        fun levelPosition(value: String): Int {
            val index = GraphicsPresets.LEVELS.indexOf(value)
            return if (index >= 0) index else 1
        }

        val currentOverall = GraphicsPresets.normalizeOverall(
            shared.getString(GraphicsPresets.PREF_OVERALL, GraphicsPresets.MEDIUM)
        )
        val overallIndex = overallValues().indexOf(currentOverall).let { if (it >= 0) it else 1 }
        overallSpinner.setSelection(overallIndex)
        for (i in groupSpinners.indices) {
            groupSpinners[i].setSelection(levelPosition(GraphicsPresets.getLevel(shared, groupKeys[i])))
        }

        overallSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val value = overallValues()[position]
                if (value == GraphicsPresets.CUSTOM)
                    return
                val levelPos = levelPosition(value)
                for (spinner in groupSpinners)
                    if (spinner.selectedItemPosition != levelPos)
                        spinner.setSelection(levelPos)
            }
        }

        for (spinner in groupSpinners) {
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val overallPos = overallSpinner.selectedItemPosition
                    if (overallPos < 0)
                        return
                    val overallValue = overallValues()[overallPos]
                    if (overallValue != GraphicsPresets.CUSTOM && position != levelPosition(overallValue)) {
                        overallSpinner.setSelection(overallValues().indexOf(GraphicsPresets.CUSTOM))
                    }
                }
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.graphics_dialog_title)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.graphics_apply, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val overall = overallValues()[overallSpinner.selectedItemPosition]
                fun selectedLevel(spinner: Spinner): String {
                    val pos = spinner.selectedItemPosition.coerceIn(0, GraphicsPresets.LEVELS.size - 1)
                    return GraphicsPresets.LEVELS[pos]
                }

                GraphicsPresets.saveSelection(
                    shared,
                    overall,
                    selectedLevel(osgSpinner),
                    selectedLevel(streamingSpinner),
                    selectedLevel(terrainSpinner),
                    selectedLevel(shadersSpinner),
                    selectedLevel(lightingSpinner),
                    selectedLevel(shadowsSpinner),
                    selectedLevel(grassSpinner)
                )
                updateGraphicsPreferenceSummary()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /**
     * Checks the specified path for a valid morrowind installation, generates config files
     * and saves the path to shared prefs if it's valid.
     * If it isn't, an error is displayed to the user.
     */
    private fun setupData(path: String) {
        val sharedPref = preferenceScreen.sharedPreferences

        // reset the setting so that it's erased on error instead of keeping
        // possibly stale value
        var gameFiles = ""

        val inst = GameInstaller(path)
        if (inst.check()) {
            inst.setNomedia()
            if (!inst.convertIni(sharedPref.getString("pref_encoding", GameInstaller.DEFAULT_CHARSET_PREF)!!)) {
                showError(R.string.data_error_title, R.string.ini_error_message)
            } else {
                val root = BuildManifestManager.normalizeGameRoot(path)
                gameFiles = root.absolutePath
                val dataDir = BuildManifestManager.dataDirForGamePath(path)
                if (dataDir.isDirectory) {
                    // PC launcher semantics: existing build.ini is authoritative;
                    // otherwise create it once using canonical content order.
                    BuildManifestManager.initializeIfMissing(activity, dataDir)
                }
            }
        } else {
            showError(R.string.data_error_title, R.string.data_error_message,
                    "https://omw.xyz.is/game.html")
        }

        with(sharedPref.edit()) {
            putString("game_files", gameFiles)
            apply()
        }
    }

    /**
     * Shows an alert dialog displaying a specific error
     * @param title Title string resource
     * @param message Message string resource
     */
    private fun showError(title: Int, message: Int, url: String? = null) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface, _: Int -> }

        if (url != null) {
            dialog.setNeutralButton(R.string.dialog_howto) { _, _ ->
                (activity as MainActivity).openUrl(url)
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        for (i in 0 until preferenceScreen.preferenceCount) {
            val preference = preferenceScreen.getPreference(i)
            if (preference is PreferenceGroup) {
                for (j in 0 until preference.preferenceCount) {
                    val singlePref = preference.getPreference(j)
                    updatePreference(singlePref, singlePref.key)
                }
            } else {
                updatePreference(preference, preference.key)
            }
        }
        updateGraphicsPreferenceSummary()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updatePreference(findPreference(key), key)
        updateGammaState()
    }

    private fun updatePreference(preference: Preference?, key: String) {
        if (preference == null)
            return
        if (preference is EditTextPreference) {
            if (key == "pref_uiScaling" && (preference.text == null || preference.text.isEmpty()))
                // Show "Auto (1.23)"
                preference.summary = MyApp.app.getString(R.string.uiScaling_auto)
                    .format(Locale.ROOT, MyApp.app.defaultScaling)
            else
                preference.summary = preference.text
        }
        // Show selected value as a summary for game_files
        if (key == "game_files") {
            preference.summary = preference.sharedPreferences.getString("game_files", "")
        }
    }

    /**
     * @brief Disable gamma preference if GLES1 is selected
     */
    private fun updateGammaState() {
        val sharedPref = preferenceScreen.sharedPreferences
        findPreference("pref_gamma").isEnabled =
                sharedPref.getString("pref_graphicsLibrary_v2", "") != "gles1"
    }

}
