/*
    ArenaMW Android graphics presets.
    Mobile-safe V15.1 keeps composite resolution at 1024 and scales only
    composite map level from -3 to -1 across presets. Shadow configuration belongs to the in-game settings only.
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
        val compositeMapLevel: Int,
        val compositeMapResolution: Int
    )

    // No Ultra/Very High mobile landscape modes. Composite resolution stays 1024;
    // presets scale composite level from -3 (fast) to -1 (quality).
    val PRESETS: Map<String, Preset> = mapOf(
        "quality" to Preset(
            osgThreading = "DrawThreadPerContext",
            pagerThreads = 5, dbThreads = 5, compileThreads = 2, maxPagedLOD = 8,
            shaderCache = true,
            viewingDistance = 6144, lodFactor = 0.80f, antialiasing = 0,
            asyncNumThreads = 2, targetFramerate = 60,
            shadersOn = true, perPixelLighting = false,
            objectPaging = true, distantTerrain = true,
            compositeMapLevel = -1, compositeMapResolution = 1024
        ),
        "balanced" to Preset(
            osgThreading = "DrawThreadPerContext",
            pagerThreads = 4, dbThreads = 4, compileThreads = 2, maxPagedLOD = 6,
            shaderCache = true,
            viewingDistance = 5120, lodFactor = 0.65f, antialiasing = 0,
            asyncNumThreads = 2, targetFramerate = 60,
            shadersOn = true, perPixelLighting = false,
            objectPaging = true, distantTerrain = false,
            compositeMapLevel = -2, compositeMapResolution = 1024
        ),
        "performance" to Preset(
            osgThreading = "CullDrawThreadPerContext",
            pagerThreads = 3, dbThreads = 3, compileThreads = 1, maxPagedLOD = 4,
            shaderCache = true,
            viewingDistance = 4096, lodFactor = 0.50f, antialiasing = 0,
            asyncNumThreads = 1, targetFramerate = 45,
            shadersOn = true, perPixelLighting = false,
            objectPaging = true, distantTerrain = false,
            compositeMapLevel = -3, compositeMapResolution = 1024
        ),
        "battery" to Preset(
            osgThreading = "SingleThreaded",
            pagerThreads = 2, dbThreads = 2, compileThreads = 1, maxPagedLOD = 3,
            shaderCache = true,
            viewingDistance = 3072, lodFactor = 0.40f, antialiasing = 0,
            asyncNumThreads = 1, targetFramerate = 30,
            shadersOn = true, perPixelLighting = false,
            objectPaging = false, distantTerrain = false,
            compositeMapLevel = -3, compositeMapResolution = 1024
        )
    )

    fun resolve(presetId: String?): Preset? {
        if (presetId.isNullOrBlank() || presetId == "auto") return null
        return PRESETS[presetId]
    }
}
