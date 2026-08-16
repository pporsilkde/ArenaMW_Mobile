/*
    Copyright (C) 2019 Ilya Zhuravlev

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

package ui.activity

import com.libopenmw.openmw.R

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.material.tabs.TabLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import file.GameInstaller
import file.BuildManifest
import kotlinx.android.synthetic.main.activity_mods.*
import mods.*
import android.view.MenuItem
import android.app.AlertDialog
import android.preference.PreferenceManager
import android.widget.Toast


class ModsActivity : AppCompatActivity() {
    private var modsReady = false
    private var manifestDirty = false
    private val manifestHandler = Handler(Looper.getMainLooper())
    private val manifestSaveRunnable = Runnable { persistManifest(false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mods)

        setSupportActionBar(findViewById(R.id.mods_toolbar))

        // Never construct the Mods database around an unset/invalid game path.
        // On a fresh install this used to crash when "Mods" was opened before
        // selecting the Morrowind resources directory.
        val gamePath = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("game_files", "").orEmpty()
        if (gamePath.isBlank() || !GameInstaller(gamePath).check()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.no_data_files_title)
                .setMessage(R.string.no_data_files_message)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
            return
        }

        // Enable the "back" icon in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Switch tabs between plugins/resources
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                flipper.displayedChild = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })

        // Import desktop-compatible build.ini before presenting the lists.
        // Editing the lists below then updates the same manifest on exit.
        BuildManifest.applyToDatabase(this)

        // Set up adapters for the lists
        setupModList(findViewById(R.id.list_mods), ModType.Plugin)
        setupModList(findViewById(R.id.list_resources), ModType.Resource)
        setupModList(findViewById(R.id.list_groundcovers), ModType.Groundcover)
        modsReady = true
    }

    /**
     * Connects a user-interface RecyclerView to underlying mod data on the disk
     * @param list The list displayed to the user
     * @param type Type of the mods this list will contain
     */
    private fun setupModList(list: RecyclerView, type: ModType) {
        val dataFiles = GameInstaller.getDataFiles(this)

        val linearLayoutManager = LinearLayoutManager(this)
        linearLayoutManager.orientation = RecyclerView.VERTICAL
        list.layoutManager = linearLayoutManager

        // Keep build.ini synchronized while checkboxes/order are edited.
        val adapter = ModsAdapter(ModsCollection(type, dataFiles, database)) {
            scheduleManifestSave()
        }

        // Set up the drag-and-drop callback
        val callback = ModMoveCallback(adapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(list)

        adapter.touchHelper = touchHelper

        list.adapter = adapter
    }


    private fun scheduleManifestSave() {
        if (!modsReady) return
        manifestDirty = true
        manifestHandler.removeCallbacks(manifestSaveRunnable)
        // Avoid a disk write for every drag step while still keeping the portable
        // manifest nearly immediately in sync with the mod database.
        manifestHandler.postDelayed(manifestSaveRunnable, 200L)
    }

    private fun persistManifest(showError: Boolean) {
        if (!modsReady || (!manifestDirty && !showError)) return
        manifestHandler.removeCallbacks(manifestSaveRunnable)
        try {
            BuildManifest.writeFromDatabase(this)
            manifestDirty = false
        } catch (e: Throwable) {
            if (showError) {
                Toast.makeText(
                    this,
                    getString(R.string.build_manifest_save_failed, e.message ?: e.javaClass.simpleName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onPause() {
        // Force the final checkbox/order state to disk before leaving the screen.
        if (modsReady) {
            manifestDirty = true
            persistManifest(true)
        }
        super.onPause()
    }

    override fun onDestroy() {
        manifestHandler.removeCallbacks(manifestSaveRunnable)
        super.onDestroy()
    }

    /**
     * Makes the "back" icon in the actionbar perform the back operation
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}
