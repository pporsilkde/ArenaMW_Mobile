/*
    ArenaMW Android graphics presets.
    Mobile-safe V12 intentionally caps terrain quality and keeps expensive
    post-processing out of the preset system. Shadows are part of the preset,
    but the launcher also exposes an explicit shadow override.
*/
package file

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
        val antialiasing: Int,
        val asyncNumThreads: Int,
        val targetFramerate: Int,
        val shadersOn: Boolean,
        val perPixelLighting: Boolean,
        val objectPaging: Boolean,
        val distantTerrain: Boolean,
        val shadowScope: String,
        val shadowResolution: Int
    )

    // No Ultra/Very High mobile landscape modes. Medium (LOD 0.80 / 2048-ish
    // composite path in ArenaMW) is the upper bound for Android.
    val PRESETS: Map<String, Preset> = mapOf(
        "quality" to Preset(
            osgThreading = "DrawThreadPerContext",
            pagerThreads = 5, dbThreads = 5, compileThreads = 2, maxPagedLOD = 8,
            shaderCache = true,
            viewingDistance = 6144, lodFactor = 0.80f, antialiasing = 0,
            asyncNumThreads = 2, targetFramerate = 60,
            shadersOn = true, perPixelLighting = false,
            objectPaging = true, distantTerrain = true,
            shadowScope = "objects", shadowResolution = 1024
        ),
        "balanced" to Preset(
            osgThreading = "DrawThreadPerContext",
            pagerThreads = 4, dbThreads = 4, compileThreads = 2, maxPagedLOD = 6,
            shaderCache = true,
            viewingDistance = 5120, lodFactor = 0.65f, antialiasing = 0,
            asyncNumThreads = 2, targetFramerate = 60,
            shadersOn = true, perPixelLighting = false,
            objectPaging = true, distantTerrain = false,
            shadowScope = "characters", shadowResolution = 512
        ),
        "performance" to Preset(
            osgThreading = "CullDrawThreadPerContext",
            pagerThreads = 3, dbThreads = 3, compileThreads = 1, maxPagedLOD = 4,
            shaderCache = true,
            viewingDistance = 4096, lodFactor = 0.50f, antialiasing = 0,
            asyncNumThreads = 1, targetFramerate = 45,
            shadersOn = true, perPixelLighting = false,
            objectPaging = true, distantTerrain = false,
            shadowScope = "off", shadowResolution = 512
        ),
        "battery" to Preset(
            osgThreading = "SingleThreaded",
            pagerThreads = 2, dbThreads = 2, compileThreads = 1, maxPagedLOD = 3,
            shaderCache = true,
            viewingDistance = 3072, lodFactor = 0.40f, antialiasing = 0,
            asyncNumThreads = 1, targetFramerate = 30,
            shadersOn = true, perPixelLighting = false,
            objectPaging = false, distantTerrain = false,
            shadowScope = "off", shadowResolution = 512
        )
    )

    fun resolve(presetId: String?): Preset? {
        if (presetId.isNullOrBlank() || presetId == "auto") return null
        return PRESETS[presetId]
    }
}
