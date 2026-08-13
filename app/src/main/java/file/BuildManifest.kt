/*
 * ArenaMW Android portable build.ini support.
 * Mirrors the standalone PC launcher manifest semantics closely:
 *   - build.ini is authoritative for ordered content
 *   - groundcover and archives are stored separately
 *   - paths are portable relative to the manifest where possible
 *   - unknown/network-only keys are ignored
 */
package file

import android.content.Context
import android.preference.PreferenceManager
import android.util.AtomicFile
import android.util.Log
import mods.ModType
import mods.ModsCollection
import mods.ModsDatabaseOpenHelper
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.Locale

data class BuildManifest(
    var formatVersion: Int = 1,
    var buildName: String = "ArenaMW",
    var dataPath: String = "Data Files",
    var language: String = "English",
    var languageSpecified: Boolean = false,
    var complete: Boolean = false,
    val contentFiles: MutableList<String> = mutableListOf(),
    val groundcoverFiles: MutableList<String> = mutableListOf(),
    val archives: MutableList<String> = mutableListOf()
)

object BuildManifestManager {
    private const val TAG = "ArenaBuildManifest"
    private const val MANIFEST_NAME = "build.ini"

    private val canonicalOrder = listOf(
        "Morrowind.esm",
        "Tribunal.esm",
        "Bloodmoon.esm",
        "GFM.esm",
        "Rebirth_Main.esm",
        "OAAB_Data.esm",
        "MFR.esm",
        "Tamriel_Data.esm",
        "TR_Mainland.esm",
        "Cyr_Main.esm",
        "Sky_Main.esm",
        "Wares-base.esm",
        "NOD_Core.esm",
        "TDoO_Main.esm",
        "Nirn_Core.esp",
        "TR_Factions.esp",
        "MFR_TR_patch.esp"
    )

    private fun isDataFiles(dir: File): Boolean =
        dir.name.equals(GameInstaller.DATA_NAME, ignoreCase = true)

    fun normalizeGameRoot(path: String): File {
        val selected = File(path).absoluteFile
        return if (isDataFiles(selected)) selected.parentFile ?: selected else selected
    }

    fun dataDirForGamePath(path: String): File {
        val selected = File(path).absoluteFile
        if (isDataFiles(selected)) return selected
        val children = selected.listFiles()
        return children?.firstOrNull { it.isDirectory && isDataFiles(it) }
            ?: File(selected, GameInstaller.DATA_NAME)
    }

    fun canonicalPathForDataDir(dataDir: File): File {
        val clean = dataDir.absoluteFile
        return if (isDataFiles(clean)) {
            File(clean.parentFile ?: clean, MANIFEST_NAME)
        } else {
            File(clean, MANIFEST_NAME)
        }
    }

    fun findForDataDir(dataDir: File): File? {
        val canonical = canonicalPathForDataDir(dataDir)
        if (canonical.isFile) return canonical

        // Compatibility with early Android/portable builds that kept it in Data Files.
        val insideData = File(dataDir, MANIFEST_NAME)
        if (insideData.isFile) return insideData

        return null
    }

    private fun decodeValue(raw: String): String {
        val value = raw.trim()
        if (value.length < 2 || !value.startsWith('"') || !value.endsWith('"')) return value
        val body = value.substring(1, value.length - 1)
        val out = StringBuilder(body.length)
        var escaped = false
        for (ch in body) {
            if (escaped) {
                out.append(when (ch) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    else -> ch
                })
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else {
                out.append(ch)
            }
        }
        if (escaped) out.append('\\')
        return out.toString()
    }

    private fun encodeValue(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (ch in value) {
            when (ch) {
                '\\', '"' -> { out.append('\\'); out.append(ch) }
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        out.append('"')
        return out.toString()
    }

    private fun parseBool(value: String): Boolean =
        value.equals("true", true) || value == "1" || value.equals("yes", true)

    private fun isBuildSection(section: String): Boolean =
        section.isEmpty() || section == "build" || section == "general" || section == "manifest"

    private fun isContentKey(key: String): Boolean =
        key == "content" || key == "plugin" || key == "esm" || key == "esp" ||
            key == "omwgame" || key == "omwaddon"

    fun canonicalLanguage(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return "English"
        val aliases = mapOf(
            "english" to "English", "английский" to "English", "anglais" to "English",
            "french" to "French", "французский" to "French", "français" to "French",
            "german" to "German", "немецкий" to "German", "deutsch" to "German",
            "italian" to "Italian", "итальянский" to "Italian", "italiano" to "Italian",
            "polish" to "Polish", "польский" to "Polish", "polski" to "Polish",
            "russian" to "Russian", "русский" to "Russian", "русский язык" to "Russian",
            "spanish" to "Spanish", "испанский" to "Spanish", "español" to "Spanish"
        )
        return aliases[value.toLowerCase(Locale.ROOT)] ?: value
    }

    fun read(file: File): BuildManifest? {
        if (!file.isFile) return null
        return try {
            val manifest = BuildManifest()
            var section = ""
            file.readLines(Charsets.UTF_8).forEach { sourceLine ->
                val line = sourceLine.trim()
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEach
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length - 1).trim().toLowerCase(Locale.ROOT)
                    return@forEach
                }
                val equals = line.indexOf('=')
                if (equals <= 0) return@forEach
                val key = line.substring(0, equals).trim().toLowerCase(Locale.ROOT)
                val value = decodeValue(line.substring(equals + 1))
                when {
                    isBuildSection(section) && (key == "format" || key == "version") ->
                        value.toIntOrNull()?.takeIf { it > 0 }?.let { manifest.formatVersion = it }
                    isBuildSection(section) && (key == "name" || key == "build-name") ->
                        manifest.buildName = value
                    isBuildSection(section) && (key == "data" || key == "data-path" || key == "datafiles") ->
                        manifest.dataPath = value
                    key == "language" || key == "locale" ||
                        ((section == "language" || section == "locale") &&
                            (key == "value" || key == "name" || key == "selected" || key == "current")) -> {
                        manifest.language = canonicalLanguage(value)
                        manifest.languageSpecified = value.trim().isNotEmpty()
                    }
                    isBuildSection(section) && (key == "complete" || key == "locked" || key == "read-only") ->
                        manifest.complete = parseBool(value)
                    isContentKey(key) -> manifest.contentFiles.add(value)
                    key == "groundcover" || key == "grass" -> manifest.groundcoverFiles.add(value)
                    key == "archive" || key == "bsa" || key == "fallback-archive" -> manifest.archives.add(value)
                    // Unknown/network-only keys are intentionally ignored, same as PC.
                }
            }
            if (manifest.buildName.trim().isEmpty()) manifest.buildName = "ArenaMW"
            manifest.language = canonicalLanguage(manifest.language)
            manifest
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read ${file.absolutePath}", e)
            null
        }
    }

    fun resolvedDataDir(manifest: BuildManifest, manifestFile: File): File {
        val parent = manifestFile.parentFile ?: manifestFile.absoluteFile.parentFile ?: File(".")
        val specified = manifest.dataPath.trim()
        if (specified.isNotEmpty()) {
            val data = File(specified)
            return if (data.isAbsolute) data.absoluteFile else File(parent, specified).absoluteFile
        }
        return if (isDataFiles(parent)) parent else {
            val sibling = File(parent, GameInstaller.DATA_NAME)
            if (sibling.isDirectory) sibling else parent
        }
    }

    private fun portableDataPath(manifestFile: File, dataDir: File): String {
        val parent = manifestFile.parentFile?.canonicalFile ?: return dataDir.absolutePath
        val data = dataDir.canonicalFile
        if (data.parentFile == parent) return data.name
        if (data == parent) return "."
        return data.absolutePath
    }

    fun write(file: File, manifest: BuildManifest): Boolean {
        return try {
            file.parentFile?.mkdirs()
            val text = buildString {
                append("# ArenaMW standalone portable build manifest\n")
                append("# Ordered entries are applied exactly as written.\n\n")
                append("[Build]\n")
                append("format=${if (manifest.formatVersion > 0) manifest.formatVersion else 1}\n")
                append("name=${encodeValue(manifest.buildName.trim().ifEmpty { "ArenaMW" })}\n")
                append("data-path=${encodeValue(manifest.dataPath)}\n")
                append("language=${encodeValue(canonicalLanguage(manifest.language))}\n")
                append("complete=${if (manifest.complete) "true" else "false"}\n\n")
                append("[Content]\n")
                manifest.contentFiles.forEach { append("content=${encodeValue(it)}\n") }
                manifest.groundcoverFiles.forEach { append("groundcover=${encodeValue(it)}\n") }
                append("\n[Archives]\n")
                manifest.archives.forEach { append("archive=${encodeValue(it)}\n") }
            }
            val atomic = AtomicFile(file)
            var stream: FileOutputStream? = null
            try {
                stream = atomic.startWrite()
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                atomic.finishWrite(stream)
            } catch (e: Exception) {
                if (stream != null) atomic.failWrite(stream)
                throw e
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ${file.absolutePath}", e)
            false
        }
    }

    private fun caseInsensitiveActual(available: List<String>, requested: String): String? =
        available.firstOrNull { it.equals(requested, ignoreCase = true) }

    fun canonicalizeFirstRun(existingEnabled: List<String>, dataDir: File): List<String> {
        val available = dataDir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
        val ordered = mutableListOf<String>()
        canonicalOrder.forEach { requested ->
            val actual = caseInsensitiveActual(available, requested)
                ?: existingEnabled.firstOrNull { it.equals(requested, true) }
            if (actual != null && ordered.none { it.equals(actual, true) }) ordered.add(actual)
        }
        existingEnabled.forEach { file ->
            if (canonicalOrder.none { it.equals(file, true) } && ordered.none { it.equals(file, true) })
                ordered.add(file)
        }
        return ordered
    }

    private fun applyOrderedSelection(collection: ModsCollection, ordered: List<String>, authoritative: Boolean) {
        collection.applyOrderedSelection(ordered, authoritative)
    }

    /**
     * Loads build.ini into launcher state. A present build.ini owns the plug-in list,
     * including an intentionally empty list, exactly like the PC launcher.
     */
    fun loadIntoLauncher(context: Context, dataDir: File): BuildManifest? {
        val file = findForDataDir(dataDir) ?: return null
        val manifest = read(file) ?: return null
        val resolvedData = resolvedDataDir(manifest, file)
        if (!resolvedData.isDirectory) {
            Log.w(TAG, "Manifest data-path does not exist: ${resolvedData.absolutePath}")
            return null
        }

        val db = ModsDatabaseOpenHelper.getInstance(context)
        applyOrderedSelection(ModsCollection(ModType.Plugin, resolvedData.absolutePath, db), manifest.contentFiles, true)
        applyOrderedSelection(ModsCollection(ModType.Groundcover, resolvedData.absolutePath, db), manifest.groundcoverFiles, true)
        if (manifest.archives.isNotEmpty()) {
            applyOrderedSelection(ModsCollection(ModType.Resource, resolvedData.absolutePath, db), manifest.archives, true)
        }

        val root = if (isDataFiles(resolvedData)) resolvedData.parentFile ?: resolvedData else resolvedData
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
            .putString("game_files", root.absolutePath)
            .putString("build_manifest_path", file.absolutePath)
            .putString("build_name", manifest.buildName)
            .putBoolean("build_complete", manifest.complete)
        if (manifest.languageSpecified) {
            editor.putString("build_language", manifest.language)
            editor.putString("pref_encoding", when (manifest.language) {
                "Polish" -> "win1250"
                "Russian" -> "win1251"
                else -> "win1252"
            })
        }
        editor.apply()
        return manifest
    }

    /** Save current Android launcher/mod state to canonical build.ini. */
    fun saveFromLauncher(context: Context, dataDir: File, markComplete: Boolean? = null): Boolean {
        if (!dataDir.isDirectory) return false
        val file = canonicalPathForDataDir(dataDir)
        val oldPath = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("build_manifest_path", "") ?: ""
        val oldFile = oldPath.takeIf { it.isNotEmpty() }?.let { File(it) }
        val existing = when {
            oldFile != null && oldFile.isFile -> read(oldFile)
            file.isFile -> read(file)
            else -> null
        } ?: BuildManifest()

        val db = ModsDatabaseOpenHelper.getInstance(context)
        val plugins = ModsCollection(ModType.Plugin, dataDir.absolutePath, db).mods
            .filter { it.enabled }.sortedBy { it.order }.map { it.filename }
        val groundcover = ModsCollection(ModType.Groundcover, dataDir.absolutePath, db).mods
            .filter { it.enabled }.sortedBy { it.order }.map { it.filename }
        val archives = ModsCollection(ModType.Resource, dataDir.absolutePath, db).mods
            .filter { it.enabled }.sortedBy { it.order }.map { it.filename }

        existing.formatVersion = 1
        existing.buildName = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("build_name", existing.buildName)?.trim().orEmpty().ifEmpty { "ArenaMW" }
        existing.dataPath = portableDataPath(file, dataDir)
        existing.language = canonicalLanguage(
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString("build_language", existing.language) ?: existing.language)
        existing.languageSpecified = true
        if (markComplete != null) existing.complete = markComplete
        existing.contentFiles.clear(); existing.contentFiles.addAll(plugins)
        existing.groundcoverFiles.clear(); existing.groundcoverFiles.addAll(groundcover)
        existing.archives.clear(); existing.archives.addAll(archives)

        if (!write(file, existing)) return false
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("build_manifest_path", file.absolutePath)
            .putString("build_name", existing.buildName)
            .putBoolean("build_complete", existing.complete)
            .apply()
        return true
    }

    /**
     * First-run behavior from PC: enable available canonical content in canonical order once,
     * then create build.ini. Later starts preserve exact manifest order.
     */
    fun initializeIfMissing(context: Context, dataDir: File): BuildManifest {
        val loaded = loadIntoLauncher(context, dataDir)
        if (loaded != null) return loaded

        val db = ModsDatabaseOpenHelper.getInstance(context)
        val plugins = ModsCollection(ModType.Plugin, dataDir.absolutePath, db)
        val current = plugins.mods.filter { it.enabled }.sortedBy { it.order }.map { it.filename }
        val canonical = canonicalizeFirstRun(current, dataDir)
        plugins.applyOrderedSelection(canonical, true)

        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("build_name", "ArenaMW")
            .putString("build_language", languageForEncoding(
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("pref_encoding", GameInstaller.DEFAULT_CHARSET_PREF) ?: GameInstaller.DEFAULT_CHARSET_PREF))
            .apply()
        saveFromLauncher(context, dataDir, markComplete = true)
        return read(canonicalPathForDataDir(dataDir)) ?: BuildManifest(complete = true)
    }

    private fun languageForEncoding(encoding: String): String = when (encoding) {
        "win1250" -> "Polish"
        "win1251" -> "Russian"
        else -> "English"
    }
}
