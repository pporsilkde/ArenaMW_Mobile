/*
    GraphicsPresets - maps a preset id (chosen by user in settings) to a concrete
    set of settings.cfg values. Called from MainActivity.startGame() so the values
    are written on every launch when the pref is set.
*/

package file

object GraphicsPresets {

    /**
     * Keys used inside settings.cfg. We write them in the "async num threads"
     * / "rendering distance" style (space-separated lower-case), matching what
     * MainActivity already writes.
     *
     * The preset covers:
     *  - OSG threading block (from user's screenshot)
     *  - OpenMW rendering/quality knobs tied to perf
     */
    data class Preset(
        val osgThreading: String,          // OSG_THREADING, e.g. "DrawThreadPerContext"
        val pagerThreads: Int,             // OSG_DATABASE_PAGER_THREADS  (2..8)
        val dbThreads: Int,                // OSG_NUM_DATABASE_THREADS
        val compileThreads: Int,           // OSG_NUM_COMPILE_THREADS
        val maxPagedLOD: Int,              // OSG_MAX_PAGEDLOD
        val shaderCache: Boolean,          // OSG_SHADER_CACHE_ENABLED

        // OpenMW / visuals
        val viewingDistance: Int,          // "viewing distance"
        val lodFactor: Float,              // "lod factor"
        val antialiasing: Int,             // "antialiasing" (MSAA samples)
        val asyncNumThreads: Int,          // "async num threads"
        val targetFramerate: Int,          // "target framerate"
        val shadersOn: Boolean,            // "force shaders"
        val perPixelLighting: Boolean,     // "force per pixel lighting"
        val objectPaging: Boolean,         // "object paging"
        val distantTerrain: Boolean        // "distant terrain"
    )

    /**
     * Map of pref value (see pref_graphics_preset_values array) -> Preset.
     * `auto` means "don't override anything".
     */
    val PRESETS: Map<String, Preset> = mapOf(
        "ultra" to Preset(
            osgThreading = "DrawThreadPerContext",
            pagerThreads = 8, dbThreads = 8, compileThreads = 4, maxPagedLOD = 16,
            shaderCache = true,
            viewingDistance = 8192, lodFactor = 1.5f, antialiasing = 4,
            asyncNumThreads = 4, targetFramerate = 60,
            shadersOn = true, perPixelLighting = true,
            objectPaging = true, distantTerrain = true
        ),
        "high" to Preset(
            osgThreading = "DrawThreadPerContext",
            pagerThreads = 6, dbThreads = 6, compileThreads = 3, maxPagedLOD = 10,
            shaderCache = true,
            viewingDistance = 6144, lodFactor = 1.2f, antialiasing = 2,
            asyncNumThreads = 4, targetFramerate = 60,
            shadersOn = true, perPixelLighting = true,
            objectPaging = true, distantTerrain = true
        ),
        "medium" to Preset(
            osgThreading = "DrawThreadPerContext",
            pagerThreads = 4, dbThreads = 4, compileThreads = 2, maxPagedLOD = 6,
            shaderCache = true,
            viewingDistance = 5120, lodFactor = 1.0f, antialiasing = 0,
            asyncNumThreads = 2, targetFramerate = 60,
            shadersOn = true, perPixelLighting = true,
            objectPaging = true, distantTerrain = false
        ),
        "low" to Preset(
            osgThreading = "CullDrawThreadPerContext",
            pagerThreads = 3, dbThreads = 3, compileThreads = 2, maxPagedLOD = 4,
            shaderCache = true,
            viewingDistance = 4096, lodFactor = 0.7f, antialiasing = 0,
            asyncNumThreads = 2, targetFramerate = 45,
            shadersOn = true, perPixelLighting = false,
            objectPaging = true, distantTerrain = false
        ),
        "performance" to Preset(
            osgThreading = "CullThreadPerCameraDrawThreadPerContext",
            pagerThreads = 2, dbThreads = 2, compileThreads = 1, maxPagedLOD = 3,
            shaderCache = true,
            viewingDistance = 3072, lodFactor = 0.5f, antialiasing = 0,
            asyncNumThreads = 1, targetFramerate = 30,
            shadersOn = false, perPixelLighting = false,
            objectPaging = false, distantTerrain = false
        ),
        "battery" to Preset(
            osgThreading = "SingleThreaded",
            pagerThreads = 2, dbThreads = 2, compileThreads = 1, maxPagedLOD = 2,
            shaderCache = true,
            viewingDistance = 2560, lodFactor = 0.4f, antialiasing = 0,
            asyncNumThreads = 1, targetFramerate = 30,
            shadersOn = false, perPixelLighting = false,
            objectPaging = false, distantTerrain = false
        ),

        // --- chip-oriented variants (rough guidance, user can still override) ---
        "chip_sd8" to Preset(                       // Snapdragon 8 Gen series
            osgThreading = "DrawThreadPerContext",
            pagerThreads = 8, dbThreads = 8, compileThreads = 4, maxPagedLOD = 16,
            shaderCache = true,
            viewingDistance = 8192, lodFactor = 1.5f, antialiasing = 4,
            asyncNumThreads = 4, targetFramerate = 60,
            shadersOn = true, perPixelLighting = true,
            objectPaging = true, distantTerrain = true
        ),
        "chip_dim9000" to Preset(                   // Dimensity 9000/9200/9300
            osgThreading = "DrawThreadPerContext",
            pagerThreads = 6, dbThreads = 6, compileThreads = 3, maxPagedLOD = 12,
            shaderCache = true,
            viewingDistance = 7168, lodFactor = 1.3f, antialiasing = 2,
            asyncNumThreads = 4, targetFramerate = 60,
            shadersOn = true, perPixelLighting = true,
            objectPaging = true, distantTerrain = true
        ),
        "chip_sd7" to Preset(                       // Snapdragon 7 series / mid-high
            osgThreading = "DrawThreadPerContext",
            pagerThreads = 4, dbThreads = 4, compileThreads = 2, maxPagedLOD = 6,
            shaderCache = true,
            viewingDistance = 5120, lodFactor = 1.0f, antialiasing = 0,
            asyncNumThreads = 2, targetFramerate = 60,
            shadersOn = true, perPixelLighting = true,
            objectPaging = true, distantTerrain = false
        ),
        "chip_dim8000" to Preset(                   // Dimensity 8000 series
            osgThreading = "DrawThreadPerContext",
            pagerThreads = 4, dbThreads = 4, compileThreads = 2, maxPagedLOD = 6,
            shaderCache = true,
            viewingDistance = 5120, lodFactor = 1.0f, antialiasing = 0,
            asyncNumThreads = 2, targetFramerate = 60,
            shadersOn = true, perPixelLighting = true,
            objectPaging = true, distantTerrain = false
        ),
        "chip_adreno_low" to Preset(                // Snapdragon 6xx / Adreno 6xx low
            osgThreading = "CullDrawThreadPerContext",
            pagerThreads = 3, dbThreads = 3, compileThreads = 2, maxPagedLOD = 4,
            shaderCache = true,
            viewingDistance = 4096, lodFactor = 0.7f, antialiasing = 0,
            asyncNumThreads = 2, targetFramerate = 45,
            shadersOn = true, perPixelLighting = false,
            objectPaging = true, distantTerrain = false
        ),
        "chip_mali_low" to Preset(                  // Helio G-series / Mali mid-low
            osgThreading = "CullThreadPerCameraDrawThreadPerContext",
            pagerThreads = 2, dbThreads = 2, compileThreads = 1, maxPagedLOD = 3,
            shaderCache = true,
            viewingDistance = 3072, lodFactor = 0.5f, antialiasing = 0,
            asyncNumThreads = 1, targetFramerate = 30,
            shadersOn = false, perPixelLighting = false,
            objectPaging = false, distantTerrain = false
        )
    )

    /**
     * Writes the preset values to the user's settings.cfg via [file.Writer.write].
     *
     * Note: OSG_* keys are stored as environment variables in many builds. Our Writer.write
     * puts them into settings.cfg; the launch code can also `Os.setenv` them. Here we
     * set both where relevant (see MainActivity patch).
     *
     * @return the picked Preset, or null if the pref is "auto"/unknown.
     */
    fun resolve(presetId: String?): Preset? {
        if (presetId.isNullOrBlank() || presetId == "auto") return null
        return PRESETS[presetId]
    }
}
