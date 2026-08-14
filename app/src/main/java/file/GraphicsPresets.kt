/*
    ArenaMW Android graphics/performance profiles for the AMW2 mobile branch.

    The Android launcher owns profile selection. The engine is still protected by
    hard runtime caps for shadow map size/distance, so a stale settings.cfg cannot
    bypass the mobile safety limits.
*/
package file

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build

object GraphicsPresets {
    const val MASTER_KEY = "pref_graphics_master"
    const val AUTO_LEVEL_KEY = "pref_graphics_auto_level"
    const val AUTO_INITIALIZED_KEY = "pref_graphics_auto_initialized_v15"
    const val OSG_KEY = "pref_graphics_osg"
    const val STREAMING_KEY = "pref_graphics_streaming"
    const val TERRAIN_KEY = "pref_graphics_terrain"
    const val SHADERS_KEY = "pref_graphics_shaders"
    const val LIGHTING_KEY = "pref_graphics_lighting"
    const val SHADOWS_KEY = "pref_graphics_shadows"
    const val GRASS_KEY = "pref_graphics_grass"

    val DETAIL_KEYS = arrayOf(OSG_KEY, STREAMING_KEY, TERRAIN_KEY, SHADERS_KEY, LIGHTING_KEY, SHADOWS_KEY, GRASS_KEY)
    val LEVELS = arrayOf("low", "medium", "high", "ultra")

    data class OsgProfile(
        val threading: String,
        val pagerThreads: Int,
        val databaseThreads: Int,
        val compileThreads: Int,
        val maxPagedLod: Int,
        val shaderCache: Boolean
    )

    data class StreamingProfile(
        val viewingDistance: Int,
        val preloadDistance: Int,
        val preloadThreads: Int,
        val preloadCacheMax: Int,
        val cacheExpiry: Int,
        val targetFramerate: Int,
        val asyncPhysicsThreads: Int
    )

    data class TerrainProfile(
        val lodFactor: Float,
        val vertexLodMod: Int,
        val compositeMapLevel: Int,
        val compositeMapResolution: Int,
        val maxCompositeGeometrySize: Float,
        val objectPagingMergeFactor: Int,
        val objectPagingMinSize: Float,
        val distantTerrain: Boolean,
        val objectPaging: Boolean
    )

    data class ShaderProfile(
        val materialQuality: String,
        val autoUsePbrMaps: Boolean,
        val enhancedPbrLighting: Boolean,
        val waterReflectionDetail: Int,
        val waterRttSize: Int
    )

    data class LightingProfile(
        val forcePerPixel: Boolean,
        val maxLights: Int,
        val radialFog: Boolean,
        val clampLighting: Boolean
    )

    data class ShadowProfile(
        val enabled: Boolean,
        val actors: Boolean,
        val objects: Boolean,
        val terrain: Boolean,
        val resolution: Int,
        val distance: Int,
        val fadeStart: Float
    )

    data class GrassProfile(
        val enabled: Boolean,
        val density: Float,
        val distance: Int,
        val minChunkSize: Float
    )

    fun normalizeLevel(value: String?): String {
        return if (value != null && LEVELS.contains(value)) value else "medium"
    }

    fun applyLevelToDetails(editor: SharedPreferences.Editor, level: String) {
        val safe = normalizeLevel(level)
        for (key in DETAIL_KEYS)
            editor.putString(key, safe)
    }

    fun ensureAutoInitialized(context: Context, prefs: SharedPreferences) {
        if (prefs.getBoolean(AUTO_INITIALIZED_KEY, false))
            return

        val detected = recommendLevel(context)
        val edit = prefs.edit()
        edit.putString(MASTER_KEY, "auto")
        edit.putString(AUTO_LEVEL_KEY, detected)
        applyLevelToDetails(edit, detected)
        edit.putBoolean(AUTO_INITIALIZED_KEY, true)
        edit.apply()
    }

    fun refreshAuto(context: Context, prefs: SharedPreferences): String {
        val detected = recommendLevel(context)
        val edit = prefs.edit()
        edit.putString(MASTER_KEY, "auto")
        edit.putString(AUTO_LEVEL_KEY, detected)
        applyLevelToDetails(edit, detected)
        edit.putBoolean(AUTO_INITIALIZED_KEY, true)
        edit.apply()
        return detected
    }

    fun recommendLevel(context: Context): String {
        var ramGb = 4.0
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            ramGb = info.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
        } catch (_: Exception) {
        }

        val cores = Runtime.getRuntime().availableProcessors()
        val hardware = (Build.HARDWARE + " " + Build.BOARD + " " + Build.MODEL).toLowerCase()

        // Conservative mobile-first scoring. RAM/CPU are reliable in the launcher;
        // the hardware string provides only a small correction for very old SoCs.
        var score = 0
        if (ramGb >= 5.5) score++
        if (ramGb >= 7.5) score++
        if (ramGb >= 10.0) score++
        if (cores >= 6) score++
        if (cores >= 8) score++
        if (hardware.contains("msm89") || hardware.contains("mt67") || hardware.contains("exynos7")) score--

        return when {
            score >= 5 -> "ultra"
            score >= 3 -> "high"
            score >= 1 -> "medium"
            else -> "low"
        }
    }

    fun osg(level: String): OsgProfile = when (normalizeLevel(level)) {
        "low" -> OsgProfile("SingleThreaded", 2, 2, 1, 3, true)
        "high" -> OsgProfile("DrawThreadPerContext", 4, 4, 2, 6, true)
        "ultra" -> OsgProfile("DrawThreadPerContext", 5, 5, 2, 8, true)
        else -> OsgProfile("CullDrawThreadPerContext", 3, 3, 1, 4, true)
    }

    fun streaming(level: String): StreamingProfile = when (normalizeLevel(level)) {
        "low" -> StreamingProfile(4096, 1000, 1, 32, 10, 45, 1)
        "high" -> StreamingProfile(6144, 1750, 2, 64, 8, 60, 2)
        "ultra" -> StreamingProfile(8192, 2250, 2, 96, 8, 60, 2)
        else -> StreamingProfile(5120, 1250, 1, 48, 10, 60, 1)
    }

    fun terrain(level: String): TerrainProfile = when (normalizeLevel(level)) {
        "low" -> TerrainProfile(0.40f, -2, -3, 512, 4.0f, 100000, 1.00f, true, true)
        "high" -> TerrainProfile(0.70f, -1, -2, 1024, 6.0f, 50000, 0.65f, true, true)
        "ultra" -> TerrainProfile(0.85f, -1, -2, 1024, 8.0f, 35000, 0.50f, true, true)
        else -> TerrainProfile(0.55f, -2, -3, 1024, 4.0f, 75000, 0.85f, true, true)
    }

    fun shaders(level: String): ShaderProfile = when (normalizeLevel(level)) {
        "low" -> ShaderProfile("none", false, false, 1, 256)
        "high" -> ShaderProfile("balanced", true, false, 3, 256)
        "ultra" -> ShaderProfile("quality", true, false, 3, 512)
        else -> ShaderProfile("simple", false, false, 2, 256)
    }

    fun lighting(level: String): LightingProfile = when (normalizeLevel(level)) {
        "low" -> LightingProfile(false, 8, false, true)
        "high" -> LightingProfile(false, 16, true, true)
        "ultra" -> LightingProfile(true, 24, true, true)
        else -> LightingProfile(false, 12, true, true)
    }

    fun shadows(level: String): ShadowProfile = when (normalizeLevel(level)) {
        "low" -> ShadowProfile(false, false, false, false, 256, 0, 0.90f)
        "high" -> ShadowProfile(true, true, true, false, 1024, 6144, 0.90f)
        "ultra" -> ShadowProfile(true, true, true, true, 1024, 8192, 0.90f)
        else -> ShadowProfile(true, true, false, false, 512, 4096, 0.90f)
    }

    fun grass(level: String): GrassProfile = when (normalizeLevel(level)) {
        "low" -> GrassProfile(false, 0.45f, 3072, 1.00f)
        "high" -> GrassProfile(true, 0.80f, 6144, 0.50f)
        "ultra" -> GrassProfile(true, 0.90f, 8192, 0.25f)
        else -> GrassProfile(true, 0.65f, 4096, 0.50f)
    }
}
