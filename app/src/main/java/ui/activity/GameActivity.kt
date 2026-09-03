/*
    Copyright (C) 2015-2017 sandstranger
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

package ui.activity

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Process
import android.preference.PreferenceManager
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import android.view.WindowManager
import android.widget.RelativeLayout
import com.libopenmw.openmw.BuildConfig
import com.libopenmw.openmw.R

import org.libsdl.app.SDLActivity

import constants.Constants
import cursor.MouseCursor
import file.GraphicsPresets
import parser.CommandlineParser
import ui.controls.Osc

import utils.Utils.hideAndroidControls

enum class MouseMode {
    Hybrid,
    Joystick,
    Touch;

    companion object {
        fun get(s: String): MouseMode {
            return when (s) {
                "joystick" -> Joystick
                "touch" -> Touch
                else -> Hybrid
            }
        }
    }
}

class GameActivity : SDLActivity() {

    private var prefs: SharedPreferences? = null
    private var osc: Osc? = null

    val layout: RelativeLayout
        get() = SDLActivity.mLayout as RelativeLayout

    override fun loadLibraries() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val physicsFPS = prefs!!.getString("pref_physicsFPS2", "")

        // ── Physics FPS ───────────────────────────────────────────────────────
        if (!physicsFPS!!.isEmpty()) {
            try {
                Os.setenv("OPENMW_PHYSICS_FPS", physicsFPS, true)
            } catch (e: ErrnoException) {
                Log.e("OpenMW", "Failed setting OPENMW_PHYSICS_FPS.")
                e.printStackTrace()
            }
        }

        System.loadLibrary("c++_shared")
        System.loadLibrary("openal")
        System.loadLibrary("SDL2")

        try {
            // ── NG-GL4ES требует GLES3 ────────────────────────────────────────
            // В отличие от старого gl4es (GLES2), NG-GL4ES использует GLES3
            // для поддержки sampler2DShadow, shadow2D, UBO и SPIRV-Cross конвертации
            Os.setenv("OPENMW_GLES_VERSION", "3", true)
            Os.setenv("LIBGL_ES", "3", true)

            // ── OSG ───────────────────────────────────────────────────────────
            Os.setenv("OSG_VERTEX_BUFFER_HINT", "VBO", true)
            // Отключить storage textures — NG-GL4ES их не поддерживает через SPIRV
            Os.setenv("OSG_GL_TEXTURE_STORAGE", "OFF", true)
            // ALL позволяет OSG использовать шейдеры для текста (NG-GL4ES их конвертирует)
            Os.setenv("OSG_TEXT_SHADER_TECHNIQUE", "ALL", true)
            Os.setenv("OSG_MIN_NEAR_FAR_RATIO", "0.0001", true)
            Os.setenv("OSG_COP_VALUE", "0x00000100", true)
            Os.setenv("OSG_NOTIFY_LEVEL", "WARN", true)

            // ── OSG Threading из preset ───────────────────────────────────────
            val preset = GraphicsPresets.resolve(prefs!!)
            Os.setenv("OSG_MAX_PAGEDLOD", preset.maxPagedLOD.toString(), true)
            Os.setenv("OSG_THREADING", preset.osgThreading, true)
            Os.setenv("OSG_NUM_DATABASE_THREADS", preset.dbThreads.toString(), true)
            Os.setenv("OSG_NUM_COMPILE_THREADS", preset.compileThreads.toString(), true)
            Os.setenv("OSG_DATABASE_PAGER_THREADS", preset.pagerThreads.toString(), true)
            Os.setenv("OSG_SHADER_CACHE_ENABLED", if (preset.shaderCache) "1" else "0", true)

            // ── NG-GL4ES специфичные переменные ──────────────────────────────
            // Keep the known-working simple converter for the general ArenaMW 0.47 renderer.
            // Complex water V3 is adapted at shader-source level instead of globally forcing
            // the unstable advanced converter that previously produced a purple frame/crash.
            Os.setenv("LIBGL_SIMPLE_SHADERCONV", "1", true)
            // Instancing через SPIRV
            Os.setenv("LIBGL_INSTANCING", "1", true)
            // Match the newer OpenMW-Android builder: keep FBO attachments on the
            // normal texture-backed path instead of forcing GL4ES FBO textures.
            Os.setenv("LIBGL_FBOFORCETEX", "0", true)
            // DXT mipmaps через NG-GL4ES
            Os.setenv("LIBGL_DXTMIPMAP", "1", true)
            // Текстуры
            Os.setenv("LIBGL_AVOID16BITS", "1", true)
            // Avoid per-frame GL4ES diagnostic overhead in normal play while
            // preserving shader compile/link errors. DEBUG/VERBOSE can re-enable it.
            val debugLevel = prefs!!.getString("pref_debug_level", "WARNING") ?: "WARNING"
            Os.setenv("LIBGL_LOG", if (debugLevel == "DEBUG" || debugLevel == "VERBOSE") "1" else "0", true)
            Os.setenv("LIBGL_LOGSHADERERROR", "1", true)
            Os.setenv("OPENMW_DISABLE_LOGS", "0", true)

            // Deliberately do not use LIBGL_SHRINK. Runtime texture shrinking adds
            // conversion work and is not a safe camera-stutter optimization.

            // ── VFX / particle safety (see patch 29) ─────────────────────────
            // Spell and hit VFX are short-lived subgraphs that are created and
            // destroyed constantly while casting. Under NG-GL4ES this means a
            // burst of tiny VBO create/delete pairs per cast, which is the worst
            // case for a Mali tiler: the buffer can still be referenced by a
            // queued tile job when OSG deletes it. ARENAMW_VFX_SAFE=1 keeps those
            // throwaway drawables on client arrays and freezes culled particle
            // systems. Set ARENAMW_VFX_SAFE=0 in the "env" setting to A/B it.
            Os.setenv("ARENAMW_VFX_SAFE", prefs!!.getString("pref_vfx_safe", "1") ?: "1", true)
            // Optional burst limiter, off by default. ARENAMW_VFX_BURST=<n> caps how
            // many *new* free VFX may spawn per 200 ms; useful to confirm whether a
            // crash is driven by simultaneous effect count (AoE hitting many actors).
            val vfxBurst = prefs!!.getString("pref_vfx_burst", "0") ?: "0"
            if (vfxBurst != "0")
                Os.setenv("ARENAMW_VFX_BURST", vfxBurst, true)

        } catch (e: ErrnoException) {
            Log.e("OpenMW", "Failed setting NG-GL4ES environment variables.")
            e.printStackTrace()
        }

        // ── Debug level OpenMW ────────────────────────────────────────────────
        val omwDebugLevel = prefs!!.getString("pref_debug_level", "")
        if (omwDebugLevel == "DEBUG")   Os.setenv("OPENMW_DEBUG_LEVEL", "DEBUG",   true)
        if (omwDebugLevel == "VERBOSE") Os.setenv("OPENMW_DEBUG_LEVEL", "VERBOSE", true)
        if (omwDebugLevel == "INFO")    Os.setenv("OPENMW_DEBUG_LEVEL", "INFO",    true)
        if (omwDebugLevel == "WARNING") Os.setenv("OPENMW_DEBUG_LEVEL", "WARNING", true)
        if (omwDebugLevel == "ERROR")   Os.setenv("OPENMW_DEBUG_LEVEL", "ERROR",   true)

        // ── Пользовательские env переменные ──────────────────────────────────
        val envline = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("envLine", "").toString()
        if (envline.isNotEmpty()) {
            val envs = envline.split(" ", "\n")
            for (entry in envs) {
                val kv = entry.split("=")
                if (kv.size == 2) Os.setenv(kv[0], kv[1], true)
            }
        }

        // ── Gamma ─────────────────────────────────────────────────────────────
        val gamma = prefs!!.getString("pref_gamma", "1.0") ?: "1.0"
        try {
            Os.setenv("OPENMW_GAMMA", gamma, true)
        } catch (e: ErrnoException) {
            Log.e("OpenMW", "Failed setting OPENMW_GAMMA.")
            e.printStackTrace()
        }

        // NG-GL4ES: загружаем libng_gl4es.so вместо старого libGL.so
        System.loadLibrary("ng_gl4es")
        System.loadLibrary("openmw")
    }

    override fun getMainSharedObject(): String {
        return applicationInfo.nativeLibraryDir + "/libopenmw.so"
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KeepScreenOn()
        getPathToJni(filesDir.parent, Constants.USER_FILE_STORAGE)
        showControls()
    }

    private fun showControls() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        mouseMode = MouseMode.get((prefs.getString("pref_mouse_mode",
            getString(R.string.pref_mouse_mode_default))!!))

        val pref_hide_controls = prefs.getBoolean(Constants.HIDE_CONTROLS, false)
        if (!pref_hide_controls) {
            val layout = layout
            // Keep left movement, right look and action-button fingers in separate
            // child streams. This prevents pointer-index stealing during multi-touch.
            //
            // V13.8: the window flag matters too. Some OEM themes ship a window
            // whose FLAG_SPLIT_TOUCH is off, and then the framework refuses to split
            // pointers at all — which is exactly the "only one finger works on this
            // phone" report. Setting it explicitly makes behaviour identical on
            // every device instead of depending on the ROM's theme defaults.
            window.addFlags(WindowManager.LayoutParams.FLAG_SPLIT_TOUCH)
            layout.setMotionEventSplittingEnabled(true)
            osc = Osc()
            osc?.placeElements(layout)
            enableSplitTouchTree(layout)
        }
        activeOsc = osc
        MouseCursor(this, osc)
    }

    private fun KeepScreenOn() {
        val needKeepScreenOn = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("pref_screen_keeper", false)
        if (needKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    public override fun onDestroy() {
        finish()
        Process.killProcess(Process.myPid())
        super.onDestroy()
    }

    /**
     * Motion-event splitting is a per-ViewGroup flag, not an inherited one. The OSC
     * builds nested RelativeLayouts (on-screen keyboard container and its key layer)
     * after the root has been configured, so walk the tree and enable it everywhere.
     * Without this a nested container routes all pointers to a single child view.
     */
    private fun enableSplitTouchTree(root: android.view.ViewGroup, depth: Int = 0) {
        if (depth > 8) return
        root.isMotionEventSplittingEnabled = true
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is android.view.ViewGroup)
                enableSplitTouchTree(child, depth + 1)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideAndroidControls(this)
            // Containers created lazily (OSK) are covered on the next focus pass.
            if (osc != null)
                enableSplitTouchTree(layout)
        }
    }

    override fun getArguments(): Array<String> {
        // ArenaMW is standalone single-player: no multiplayer connection arguments.
        return emptyArray()
    }

    private external fun getPathToJni(path_global: String, path_user: String)

    companion object {
        var mouseMode = MouseMode.Hybrid
        @JvmStatic var activeOsc: Osc? = null

        @JvmStatic
        fun showOsk(): Boolean {
            val osc = activeOsc ?: return false
            osc.onTextInputRequested()
            return true
        }

        @JvmStatic
        fun hideOsk(): Boolean {
            val osc = activeOsc ?: return false
            osc.onTextInputDismissed()
            return true
        }

        @JvmStatic
        fun isOskVisible(): Boolean {
            return activeOsc?.isKeyboardVisible() ?: false
        }
    }
}
