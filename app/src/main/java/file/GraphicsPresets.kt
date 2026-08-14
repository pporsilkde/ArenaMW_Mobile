/* ArenaMW Android mobile graphics configuration. */
package file

import android.content.SharedPreferences
import constants.Constants

object GraphicsPresets {
    data class Preset(
        val osgThreading: String,
        val pagerThreads: Int,
        val dbThreads: Int,
        val compileThreads: Int,
        val maxPagedLOD: Int,
        val shaderCache: Boolean,
        val viewingDistance: Int,
        val lodFactor: Float,
        val vertexLodMod: Int,
        val compositeMapLevel: Int,
        val compositeMapResolution: Int,
        val objectPaging: Boolean,
        val distantTerrain: Boolean,
        val preloadDistance: Int,
        val preloadThreads: Int,
        val targetFramerate: Int,
        val frameLimit: Float,
        val waterMode: String,
        val waterRtt: Int,
        val waterRefraction: Boolean,
        val waterReflectionDetail: Int,
        val shadowScope: String,
        val shadowResolution: Int,
        val shadowDistance: Int,
        val grassEnabled: Boolean,
        val grassDensity: Float,
        val grassDistance: Int,
        val shaderProfile: String,
        val asyncNumThreads: Int = 1
    )

    val PRESETS: Map<String, Preset> = mapOf(
        // Based on the supplied legacy low-end defaults: 4096 view, LOD .40,
        // 1000 preload / one worker, 256 water RTT, shadows off, 1024 cap.
        "very_low" to Preset(
            "CullDrawThreadPerContext", 1, 1, 1, 4, true,
            4096, .40f, -3, -3, 512, true, true,
            1000, 1, 60, 45.03f, "simple", 256, false, 2,
            "off", 1024, 8192, true, .80f, 7100, "compatibility"
        ),
        "performance" to Preset(
            "CullDrawThreadPerContext", 1, 1, 1, 4, true,
            4096, .50f, -2, -3, 1024, true, false,
            1200, 1, 60, 45.0f, "simple", 256, false, 2,
            "off", 512, 4096, true, .65f, 5000, "compatibility"
        ),
        "balanced" to Preset(
            "CullDrawThreadPerContext", 1, 1, 1, 6, true,
            5120, .65f, -1, -2, 1024, true, true,
            1600, 1, 60, 60.0f, "new", 256, true, 2,
            "characters", 512, 5120, true, .80f, 6000, "compatibility"
        ),
        "quality" to Preset(
            "CullDrawThreadPerContext", 2, 1, 1, 8, true,
            6144, .80f, -1, -2, 2048, true, true,
            2000, 1, 60, 60.0f, "new", 512, true, 3,
            "objects", 1024, 6144, true, 1.0f, 7100, "standard"
        ),
        "battery" to Preset(
            "SingleThreaded", 1, 1, 1, 3, true,
            3072, .40f, -3, -3, 512, false, false,
            800, 1, 30, 30.0f, "simple", 256, false, 1,
            "off", 512, 3072, false, .50f, 3500, "compatibility"
        )
    )

    fun resolve(presetId: String?): Preset? {
        if (presetId.isNullOrBlank() || presetId == "auto") return PRESETS["balanced"]
        return PRESETS[presetId]
    }

    fun resolve(prefs: SharedPreferences): Preset {
        val id = prefs.getString("pref_graphics_preset", "balanced") ?: "balanced"
        if (id != "custom") return resolve(id) ?: PRESETS.getValue("balanced")
        val base = PRESETS.getValue("balanced")
        val terrain = prefs.getString("pref_gfx_terrain", "balanced") ?: "balanced"
        val terrainValues = when (terrain) {
            "very_low" -> floatArrayOf(.40f, -3f, -3f, 512f)
            "low" -> floatArrayOf(.50f, -2f, -3f, 1024f)
            "medium" -> floatArrayOf(.80f, -1f, -2f, 2048f)
            else -> floatArrayOf(.65f, -1f, -2f, 1024f)
        }
        val preload = prefs.getString("pref_gfx_preload", "balanced") ?: "balanced"
        val preloadDistance = when (preload) { "low" -> 1000; "high" -> 2200; else -> 1600 }
        val preloadThreads = if (preload == "high") 2 else 1
        val water = prefs.getString("pref_gfx_water", "balanced") ?: "balanced"
        // Low uses the legacy/simple ArenaMW shader. Balanced/High use PBR water.
        // Keep the old low/balanced/high preference IDs for upgrade compatibility.
        val waterMode = if (water == "low") "simple" else "new"
        val waterRtt = when (water) { "high" -> 512; else -> 256 }
        val waterRefraction = water != "low"
        val waterReflection = when (water) { "low" -> 1; "high" -> 3; else -> 2 }
        val grass = prefs.getString("pref_gfx_grass", "balanced") ?: "balanced"
        val grassEnabled = grass != "off"
        val grassDensity = when (grass) { "low" -> .55f; "high" -> 1.0f; else -> .80f }
        val grassDistance = when (grass) { "low" -> 4000; "high" -> 7100; else -> 6000 }
        return base.copy(
            viewingDistance = (prefs.getString("pref_gfx_view_distance", "5120") ?: "5120").toIntOrNull()?.coerceIn(3072, 8192) ?: 5120,
            lodFactor = terrainValues[0], vertexLodMod = terrainValues[1].toInt(),
            compositeMapLevel = terrainValues[2].toInt(), compositeMapResolution = terrainValues[3].toInt(),
            preloadDistance = preloadDistance, preloadThreads = preloadThreads,
            waterMode = waterMode, waterRtt = waterRtt, waterRefraction = waterRefraction, waterReflectionDetail = waterReflection,
            shadowScope = prefs.getString("pref_gfx_shadows", "characters") ?: "characters",
            shadowResolution = ((prefs.getString("pref_gfx_shadow_map", "512") ?: "512").toIntOrNull() ?: 512).coerceIn(512, 1024),
            shadowDistance = ((prefs.getString("pref_gfx_shadow_distance", "5120") ?: "5120").toIntOrNull() ?: 5120).coerceIn(1024, 8192),
            grassEnabled = grassEnabled, grassDensity = grassDensity, grassDistance = grassDistance,
            shaderProfile = prefs.getString("pref_gfx_shaders", "compatibility") ?: "compatibility"
        )
    }

    fun applyToSettings(prefs: SharedPreferences) {
        val p = resolve(prefs)
        val cfg = Constants.USER_CONFIG + "/settings.cfg"
        fun w(section: String, key: String, value: Any) = Writer.write(cfg, section, key, value.toString())
        fun b(value: Boolean) = if (value) "true" else "false"

        w("Camera", "viewing distance", p.viewingDistance)
        w("Terrain", "distant terrain", b(p.distantTerrain))
        w("Terrain", "lod factor", p.lodFactor)
        w("Terrain", "vertex lod mod", p.vertexLodMod)
        w("Terrain", "composite map level", p.compositeMapLevel)
        w("Terrain", "composite map resolution", p.compositeMapResolution)
        w("Terrain", "max composite geometry size", if (p.lodFactor <= .5f) "4.0" else if (p.lodFactor <= .65f) "6.0" else "8.0")
        w("Terrain", "object paging", b(p.objectPaging))
        w("Terrain", "object paging active grid", "true")
        w("Terrain", "object paging merge factor", if (p.lodFactor <= .4f) "80000" else if (p.lodFactor <= .65f) "50000" else "30000")
        w("Terrain", "object paging min size", if (p.lodFactor <= .4f) "1" else if (p.lodFactor <= .65f) "0.65" else "0.50")

        w("Cells", "preload enabled", "true")
        w("Cells", "preload num threads", p.preloadThreads)
        w("Cells", "preload exterior grid", "true")
        w("Cells", "preload fast travel", "false")
        w("Cells", "preload doors", "false")
        w("Cells", "preload distance", p.preloadDistance)
        w("Cells", "preload instances", "true")
        w("Cells", "preload cell cache min", "16")
        w("Cells", "preload cell cache max", "64")
        w("Cells", "preload cell expiry delay", "5")
        w("Cells", "prediction time", "2")
        w("Cells", "cache expiry delay", "5")
        w("Cells", "target framerate", p.targetFramerate)
        w("Physics", "async num threads", p.asyncNumThreads)
        w("Video", "antialiasing", "0")
        w("Video", "framerate limit", p.frameLimit)

        // Stable shader path. The launcher never enables the known black-screen
        // native depth effects, regardless of stale settings.cfg values.
        w("Shaders", "force shaders", "true")
        w("Shaders", "force per pixel lighting", "false")
        w("Shaders", "lighting method", "shaders compatibility")
        w("Shaders", "enhanced pbr lighting", "false")
        val standard = p.shaderProfile == "standard"
        w("Shaders", "material quality", if (standard) "balanced" else "none")
        w("Shaders", "auto use object normal maps", b(standard))
        w("Shaders", "auto use object specular maps", b(standard))
        w("Shaders", "auto use terrain normal maps", b(standard))
        w("Shaders", "auto use terrain specular maps", b(standard))
        w("Shaders", "antialias alpha test", "false")
        w("Shaders", "hdr lighting", "false")
        w("Shaders", "bloom enabled", "false")
        w("Shaders", "native ssr enabled", "false")
        w("Shaders", "smaa enabled", "false")
        w("Shaders", "atmospheric fog enabled", "false")
        w("Shaders", "god rays enabled", "false")

        // Explicit mode prevents stale settings.cfg from forcing PBR water on low presets.
        // "simple" = ArenaMW legacy/simple shader, "new" = current PBR water.
        w("Water", "shader mode", p.waterMode)
        w("Water", "shader", b(p.waterMode != "off"))
        w("Water", "rtt size", p.waterRtt)
        w("Water", "refraction", b(p.waterRefraction))
        w("Water", "shader water ripples", "true")
        w("Water", "reflection detail", p.waterReflectionDetail)

        val shadowOn = p.shadowScope != "off"
        w("Shadows", "enable shadows", b(shadowOn))
        w("Shadows", "player shadows", b(shadowOn))
        w("Shadows", "actor shadows", b(p.shadowScope == "characters" || p.shadowScope == "objects"))
        w("Shadows", "object shadows", b(p.shadowScope == "objects"))
        w("Shadows", "terrain shadows", "false")
        w("Shadows", "enable indoor shadows", "false")
        w("Shadows", "shadow map resolution", p.shadowResolution.coerceAtMost(1024))
        w("Shadows", "number of shadow maps", if (shadowOn) "2" else "1")
        w("Shadows", "maximum shadow map distance", p.shadowDistance.coerceIn(0, 8192))
        w("Shadows", "shadow fade start", "0.82")
        w("Shadows", "allow shadow map overlap", "false")
        w("Shadows", "enhanced filtering", "false")

        w("Groundcover", "enabled", b(p.grassEnabled))
        w("Groundcover", "density", p.grassDensity)
        w("Groundcover", "rendering distance", p.grassDistance.coerceAtMost(8192))
    }
}
