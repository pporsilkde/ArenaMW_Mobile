/*
    ArenaMW Android graphics profiles.

    V17 turns the old single launcher preset into a modern group-based profile.
    The launcher only writes OpenMW settings after the user changes a launcher
    profile (or after the first-run auto profile is selected). This is important:
    settings changed later from OpenMW's own in-game menu are not silently reset
    on every launcher start.
*/
package file

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences

import java.util.Locale

object GraphicsPresets {
    const val PREF_OVERALL = "pref_graphics_preset"
    const val PREF_OSG = "pref_graphics_osg_preset"
    const val PREF_STREAMING = "pref_graphics_streaming_preset"
    const val PREF_TERRAIN = "pref_graphics_terrain_preset"
    const val PREF_SHADERS = "pref_graphics_shaders_preset"
    const val PREF_LIGHTING = "pref_graphics_lighting_preset"
    const val PREF_SHADOWS = "pref_graphics_shadows_preset"
    const val PREF_GRASS = "pref_graphics_grass_preset"

    const val PREF_INITIALIZED = "arenamw_graphics_profiles_v16_initialized"
    const val PREF_DIRTY = "arenamw_graphics_profiles_v16_dirty"
    const val PREF_AUTO_DETECTED = "arenamw_graphics_profiles_v16_auto_detected"
    const val PREF_REVISION = "arenamw_graphics_profiles_revision"
    const val CURRENT_REVISION = 17

    const val LOW = "low"
    const val MEDIUM = "medium"
    const val HIGH = "high"
    const val ULTRA = "ultra"
    const val CUSTOM = "custom"

    val LEVELS = arrayOf(LOW, MEDIUM, HIGH, ULTRA)

    data class OsgPreset(
        val osgThreading: String,
        val pagerThreads: Int,
        val dbThreads: Int,
        val compileThreads: Int,
        val maxPagedLOD: Int,
        val asyncNumThreads: Int
    )

    data class StreamingPreset(
        val viewingDistance: Int,
        val preloadDistance: Int,
        val preloadThreads: Int,
        val targetFramerate: Int,
        val cacheExpiryDelay: Int,
        val cacheMin: Int,
        val cacheMax: Int
    )

    data class TerrainPreset(
        val lodFactor: Float,
        val vertexLodMod: Int,
        val compositeMapLevel: Int,
        val compositeMapResolution: Int,
        val maxCompositeGeometrySize: Float,
        val objectPagingMergeFactor: Int,
        val objectPagingMinSize: Float
    )

    data class ShaderPreset(
        val materialQuality: String,
        val objectNormalMaps: Boolean,
        val objectSpecularMaps: Boolean,
        val terrainNormalMaps: Boolean,
        val terrainSpecularMaps: Boolean
    )

    data class LightingPreset(
        val perPixelLighting: Boolean,
        val maxLights: Int,
        val maximumLightDistance: Int,
        val lightBoundsMultiplier: Float,
        val lightFadeStart: Float
    )

    data class ShadowPreset(
        val enabled: Boolean,
        val actorShadows: Boolean,
        val playerShadows: Boolean,
        val objectShadows: Boolean,
        val terrainShadows: Boolean,
        val indoorShadows: Boolean,
        val numberOfShadowMaps: Int,
        val shadowMapResolution: Int,
        val maximumShadowMapDistance: Int,
        val shadowFadeStart: Float,
        val filterSoftness: Float
    )

    data class GrassPreset(
        val enabled: Boolean,
        val density: Float,
        val renderingDistance: Float,
        val minChunkSize: Float
    )

    // Medium deliberately keeps the old APK's proven OSG 4/4/2 + MaxPagedLOD 6 base.
    private val osgPresets = mapOf(
        LOW to OsgPreset("CullDrawThreadPerContext", 2, 2, 1, 4, 1),
        MEDIUM to OsgPreset("DrawThreadPerContext", 4, 4, 2, 6, 1),
        HIGH to OsgPreset("DrawThreadPerContext", 5, 5, 2, 8, 2),
        ULTRA to OsgPreset("DrawThreadPerContext", 6, 6, 3, 10, 3)
    )

    // Medium is anchored to the user's proven old-APK defaults: 4096 view distance,
    // 1000 preload, one preload worker, 60 FPS target and a 16..64 cell cache.
    private val streamingPresets = mapOf(
        LOW to StreamingPreset(3072, 750, 1, 45, 5, 16, 48),
        MEDIUM to StreamingPreset(4096, 1000, 1, 60, 5, 16, 64),
        HIGH to StreamingPreset(8192, 3000, 2, 60, 10, 16, 96),
        ULTRA to StreamingPreset(16384, 5000, 3, 60, 15, 16, 128)
    )

    // Medium mirrors the tested old APK terrain baseline. Higher levels increase
    // detail gradually without exceeding the Android-safe 1024 composite map cap.
    private val terrainPresets = mapOf(
        LOW to TerrainPreset(0.35f, -3, -3, 512, 4.0f, 100000, 1.00f),
        MEDIUM to TerrainPreset(0.40f, -3, -3, 512, 4.0f, 80000, 1.00f),
        HIGH to TerrainPreset(0.65f, -2, -2, 1024, 4.0f, 50000, 0.65f),
        ULTRA to TerrainPreset(0.80f, -1, -1, 1024, 4.0f, 30000, 0.50f)
    )

    // Android "Ultra" is intentionally capped at ArenaMW's quality material path;
    // it does not enable desktop-only HDR/post-processing or GL4ES texture shrink.
    private val shaderPresets = mapOf(
        LOW to ShaderPreset("none", false, false, false, false),
        MEDIUM to ShaderPreset("simple", true, false, false, false),
        HIGH to ShaderPreset("balanced", true, true, true, false),
        ULTRA to ShaderPreset("quality", true, true, true, true)
    )

    private val lightingPresets = mapOf(
        LOW to LightingPreset(false, 8, 4096, 0.90f, 0.35f),
        MEDIUM to LightingPreset(false, 16, 8192, 0.90f, 0.27f),
        HIGH to LightingPreset(true, 24, 8192, 1.20f, 0.27f),
        ULTRA to LightingPreset(true, 32, 8192, 1.50f, 0.25f)
    )

    // Hard mobile limits requested for this port:
    //   shadow map resolution <= 1024
    //   maximum shadow distance <= 8192
    private val shadowPresets = mapOf(
        LOW to ShadowPreset(false, false, false, false, false, false, 1, 512, 3072, 0.90f, 0.85f),
        MEDIUM to ShadowPreset(true, true, true, false, false, false, 3, 512, 4096, 0.90f, 1.00f),
        HIGH to ShadowPreset(true, true, true, true, false, false, 3, 1024, 8192, 0.90f, 1.05f),
        ULTRA to ShadowPreset(true, true, true, true, true, true, 3, 1024, 8192, 0.90f, 1.10f)
    )

    private val grassPresets = mapOf(
        LOW to GrassPreset(true, 0.45f, 4096f, 1.0f),
        MEDIUM to GrassPreset(true, 0.80f, 7100f, 0.5f),
        HIGH to GrassPreset(true, 0.90f, 8192f, 0.5f),
        ULTRA to GrassPreset(true, 1.00f, 9830.4f, 0.25f)
    )

    private fun normalizeLevel(level: String?): String {
        return if (level != null && LEVELS.contains(level)) level else MEDIUM
    }

    /**
     * One-time first-launch selection. Existing users are migrated from the old
     * single preset when possible; a fresh install gets a hardware-derived level.
     */
    fun ensureInitialized(context: Context, prefs: SharedPreferences): String {
        if (prefs.getBoolean(PREF_INITIALIZED, false)) {
            val overall = normalizeOverall(prefs.getString(PREF_OVERALL, MEDIUM))
            if (prefs.getInt(PREF_REVISION, 0) < CURRENT_REVISION) {
                // V17 fixes profile inconsistencies (water mode, old-APK Medium values
                // and shadow distance). Preserve custom group choices, but re-apply
                // the selected profile once so existing installs receive the fixes.
                val editor = prefs.edit()
                    .putBoolean(PREF_DIRTY, true)
                    .putInt(PREF_REVISION, CURRENT_REVISION)
                if (overall != CUSTOM) {
                    editor.putString(PREF_OSG, overall)
                    editor.putString(PREF_STREAMING, overall)
                    editor.putString(PREF_TERRAIN, overall)
                    editor.putString(PREF_SHADERS, overall)
                    editor.putString(PREF_LIGHTING, overall)
                    editor.putString(PREF_SHADOWS, overall)
                    editor.putString(PREF_GRASS, overall)
                }
                editor.apply()
            }
            return overall
        }

        val old = prefs.getString(PREF_OVERALL, null)
        val migrated = when (old) {
            "battery", "performance", LOW -> LOW
            "balanced", MEDIUM -> MEDIUM
            "quality", HIGH -> HIGH
            ULTRA -> ULTRA
            else -> null
        }
        val selected = migrated ?: detectRecommendedLevel(context)

        prefs.edit()
            .putString(PREF_OVERALL, selected)
            .putString(PREF_OSG, selected)
            .putString(PREF_STREAMING, selected)
            .putString(PREF_TERRAIN, selected)
            .putString(PREF_SHADERS, selected)
            .putString(PREF_LIGHTING, selected)
            .putString(PREF_SHADOWS, selected)
            .putString(PREF_GRASS, selected)
            .putString(PREF_AUTO_DETECTED, selected)
            .putInt(PREF_REVISION, CURRENT_REVISION)
            .putBoolean(PREF_DIRTY, true)
            .putBoolean(PREF_INITIALIZED, true)
            .apply()

        return selected
    }

    fun detectRecommendedLevel(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val ramGb = info.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()
        val gles = am.deviceConfigurationInfo.reqGlEsVersion
        val gles3 = gles >= 0x00030000
        val gles32 = gles >= 0x00030002

        return when {
            ramGb >= 10.0 && cores >= 8 && gles32 -> ULTRA
            ramGb >= 6.0 && cores >= 8 && gles3 -> HIGH
            ramGb >= 4.0 && cores >= 6 && gles3 -> MEDIUM
            else -> LOW
        }
    }

    fun normalizeOverall(value: String?): String {
        return when (value) {
            LOW, MEDIUM, HIGH, ULTRA, CUSTOM -> value
            "battery", "performance" -> LOW
            "balanced" -> MEDIUM
            "quality" -> HIGH
            else -> MEDIUM
        }
    }

    fun getLevel(prefs: SharedPreferences, key: String): String {
        return normalizeLevel(prefs.getString(key, MEDIUM))
    }

    fun getOsgPreset(prefs: SharedPreferences): OsgPreset = osgPresets[getLevel(prefs, PREF_OSG)]!!

    /**
     * Called only by the graphics dialog's Apply button. Selecting a global
     * level aligns every group. Custom preserves the independently selected rows.
     */
    fun saveSelection(
        prefs: SharedPreferences,
        overall: String,
        osg: String,
        streaming: String,
        terrain: String,
        shaders: String,
        lighting: String,
        shadows: String,
        grass: String
    ) {
        val normalizedOverall = normalizeOverall(overall)
        val editor = prefs.edit().putString(PREF_OVERALL, normalizedOverall)

        if (normalizedOverall == CUSTOM) {
            editor.putString(PREF_OSG, normalizeLevel(osg))
            editor.putString(PREF_STREAMING, normalizeLevel(streaming))
            editor.putString(PREF_TERRAIN, normalizeLevel(terrain))
            editor.putString(PREF_SHADERS, normalizeLevel(shaders))
            editor.putString(PREF_LIGHTING, normalizeLevel(lighting))
            editor.putString(PREF_SHADOWS, normalizeLevel(shadows))
            editor.putString(PREF_GRASS, normalizeLevel(grass))
        } else {
            editor.putString(PREF_OSG, normalizedOverall)
            editor.putString(PREF_STREAMING, normalizedOverall)
            editor.putString(PREF_TERRAIN, normalizedOverall)
            editor.putString(PREF_SHADERS, normalizedOverall)
            editor.putString(PREF_LIGHTING, normalizedOverall)
            editor.putString(PREF_SHADOWS, normalizedOverall)
            editor.putString(PREF_GRASS, normalizedOverall)
        }

        editor.putBoolean(PREF_DIRTY, true).apply()
    }

    /**
     * Writes launcher-owned values only after the launcher profile has changed.
     * In-game adjustments made afterwards stay authoritative on future launches.
     */
    fun applyPending(settingsFile: String, prefs: SharedPreferences) {
        if (!prefs.getBoolean(PREF_DIRTY, false))
            return

        val streaming = streamingPresets[getLevel(prefs, PREF_STREAMING)]!!
        val terrain = terrainPresets[getLevel(prefs, PREF_TERRAIN)]!!
        val shaders = shaderPresets[getLevel(prefs, PREF_SHADERS)]!!
        val lighting = lightingPresets[getLevel(prefs, PREF_LIGHTING)]!!
        val shadows = shadowPresets[getLevel(prefs, PREF_SHADOWS)]!!
        val grass = grassPresets[getLevel(prefs, PREF_GRASS)]!!
        val osg = osgPresets[getLevel(prefs, PREF_OSG)]!!

        Writer.write(settingsFile, "Camera", "viewing distance", streaming.viewingDistance.toString())
        Writer.write(settingsFile, "Cells", "preload enabled", "true")
        Writer.write(settingsFile, "Cells", "preload num threads", streaming.preloadThreads.toString())
        Writer.write(settingsFile, "Cells", "preload distance", streaming.preloadDistance.toString())
        Writer.write(settingsFile, "Cells", "preload exterior grid", "true")
        Writer.write(settingsFile, "Cells", "preload instances", "true")
        Writer.write(settingsFile, "Cells", "preload cell cache min", streaming.cacheMin.toString())
        Writer.write(settingsFile, "Cells", "preload cell cache max", streaming.cacheMax.toString())
        Writer.write(settingsFile, "Cells", "preload cell expiry delay", streaming.cacheExpiryDelay.toString())
        Writer.write(settingsFile, "Cells", "cache expiry delay", streaming.cacheExpiryDelay.toString())
        Writer.write(settingsFile, "Cells", "target framerate", streaming.targetFramerate.toString())
        Writer.write(settingsFile, "Physics", "async num threads", osg.asyncNumThreads.toString())

        Writer.write(settingsFile, "Terrain", "distant terrain", "true")
        Writer.write(settingsFile, "Terrain", "object paging", "true")
        Writer.write(settingsFile, "Terrain", "lod factor", format(terrain.lodFactor))
        Writer.write(settingsFile, "Terrain", "vertex lod mod", terrain.vertexLodMod.toString())
        Writer.write(settingsFile, "Terrain", "composite map level", terrain.compositeMapLevel.toString())
        Writer.write(settingsFile, "Terrain", "composite map resolution", terrain.compositeMapResolution.toString())
        Writer.write(settingsFile, "Terrain", "max composite geometry size", format(terrain.maxCompositeGeometrySize))
        Writer.write(settingsFile, "Terrain", "object paging active grid", "true")
        Writer.write(settingsFile, "Terrain", "object paging merge factor", terrain.objectPagingMergeFactor.toString())
        Writer.write(settingsFile, "Terrain", "object paging min size", format(terrain.objectPagingMinSize))

        Writer.write(settingsFile, "Shaders", "force shaders", "true")
        Writer.write(settingsFile, "Shaders", "material quality", shaders.materialQuality)
        Writer.write(settingsFile, "Shaders", "auto use object normal maps", bool(shaders.objectNormalMaps))
        Writer.write(settingsFile, "Shaders", "auto use object specular maps", bool(shaders.objectSpecularMaps))
        Writer.write(settingsFile, "Shaders", "auto use terrain normal maps", bool(shaders.terrainNormalMaps))
        Writer.write(settingsFile, "Shaders", "auto use terrain specular maps", bool(shaders.terrainSpecularMaps))

        Writer.write(settingsFile, "Shaders", "lighting method", "shaders compatibility")
        Writer.write(settingsFile, "Shaders", "force per pixel lighting", bool(lighting.perPixelLighting))
        Writer.write(settingsFile, "Shaders", "max lights", lighting.maxLights.toString())
        Writer.write(settingsFile, "Shaders", "maximum light distance", lighting.maximumLightDistance.toString())
        Writer.write(settingsFile, "Shaders", "light bounds multiplier", format(lighting.lightBoundsMultiplier))
        Writer.write(settingsFile, "Shaders", "light fade start", format(lighting.lightFadeStart))

        val safeShadowResolution = shadows.shadowMapResolution.coerceAtMost(1024)
        val safeShadowDistance = shadows.maximumShadowMapDistance.coerceAtMost(8192)
        Writer.write(settingsFile, "Shadows", "enable shadows", bool(shadows.enabled))
        Writer.write(settingsFile, "Shadows", "actor shadows", bool(shadows.actorShadows))
        Writer.write(settingsFile, "Shadows", "player shadows", bool(shadows.playerShadows))
        Writer.write(settingsFile, "Shadows", "object shadows", bool(shadows.objectShadows))
        Writer.write(settingsFile, "Shadows", "terrain shadows", bool(shadows.terrainShadows))
        Writer.write(settingsFile, "Shadows", "enable indoor shadows", bool(shadows.indoorShadows))
        Writer.write(settingsFile, "Shadows", "number of shadow maps", shadows.numberOfShadowMaps.toString())
        Writer.write(settingsFile, "Shadows", "shadow map resolution", safeShadowResolution.toString())
        Writer.write(settingsFile, "Shadows", "maximum shadow map distance", safeShadowDistance.toString())
        Writer.write(settingsFile, "Shadows", "shadow fade start", format(shadows.shadowFadeStart))
        Writer.write(settingsFile, "Shadows", "enhanced filtering", "true")
        Writer.write(settingsFile, "Shadows", "filter softness", format(shadows.filterSoftness))
        Writer.write(settingsFile, "Shadows", "adaptive softness", "true")

        Writer.write(settingsFile, "Groundcover", "enabled", bool(grass.enabled))
        Writer.write(settingsFile, "Groundcover", "density", format(grass.density))
        Writer.write(settingsFile, "Groundcover", "rendering distance", format(grass.renderingDistance))
        Writer.write(settingsFile, "Groundcover", "min chunk size", format(grass.minChunkSize))

        // V17: PBR water is intentionally not exposed on Android. Every launcher
        // graphics profile uses the stable ArenaMW legacy/simple shader water.
        Writer.write(settingsFile, "Water", "shader mode", "simple")
        Writer.write(settingsFile, "Water", "shader", "true")
        Writer.write(settingsFile, "Water", "rtt size", "256")
        Writer.write(settingsFile, "Water", "refraction", "false")
        Writer.write(settingsFile, "Water", "reflection detail", "2")

        prefs.edit().putBoolean(PREF_DIRTY, false).apply()
    }

    private fun bool(value: Boolean) = if (value) "true" else "false"

    private fun format(value: Float): String {
        return String.format(Locale.ROOT, "%.2f", value)
    }
}
