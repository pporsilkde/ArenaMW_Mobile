package file

import android.content.Context
import android.preference.PreferenceManager
import mods.ModType
import mods.ModsCollection
import mods.ModsDatabaseOpenHelper
import java.io.File

/** Portable ArenaMW build.ini compatible with the desktop standalone manifest. */
object BuildManifest {
    data class Data(
        var name: String = "ArenaMW",
        var dataPath: String = "Data Files",
        var language: String = "English",
        val content: MutableList<String> = mutableListOf(),
        val groundcover: MutableList<String> = mutableListOf(),
        val archives: MutableList<String> = mutableListOf()
    )

    private fun manifestFile(ctx: Context): File? {
        val rootPath = PreferenceManager.getDefaultSharedPreferences(ctx).getString("game_files", "") ?: ""
        if (rootPath.isBlank()) return null
        val root = File(rootPath)
        val canonical = File(root, "build.ini")
        val data = File(GameInstaller(rootPath).findDataFiles())
        val nested = File(data, "build.ini")
        return when { canonical.exists() -> canonical; nested.exists() -> nested; else -> canonical }
    }

    private fun unquote(v: String): String {
        val t = v.trim()
        if (t.length >= 2 && t.first() == '"' && t.last() == '"')
            return t.substring(1, t.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        return t
    }

    private fun quote(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun read(ctx: Context): Data? {
        val f = manifestFile(ctx) ?: return null
        if (!f.exists()) return null
        val out = Data()
        var section = ""
        f.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEachLine
            if (line.startsWith("[") && line.endsWith("]")) { section = line.substring(1, line.length - 1).toLowerCase(); return@forEachLine }
            val eq = line.indexOf('='); if (eq <= 0) return@forEachLine
            val key = line.substring(0, eq).trim().toLowerCase()
            val value = unquote(line.substring(eq + 1))
            when (section) {
                "build" -> when (key) { "name" -> out.name = value; "data-path" -> out.dataPath = value; "language" -> out.language = value }
                "content" -> when (key) { "content", "plugin", "esm", "esp", "omwgame", "omwaddon" -> out.content.add(value); "groundcover", "grass" -> out.groundcover.add(value) }
                "archives" -> if (key == "archive" || key == "bsa" || key == "fallback-archive") out.archives.add(value)
            }
            // Network-only and unknown keys are intentionally ignored, matching standalone ArenaMW.
        }
        return out
    }

    private fun collect(ctx: Context): Data {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val dataFiles = GameInstaller.getDataFiles(ctx)
        val db = ModsDatabaseOpenHelper.getInstance(ctx)
        val mf = manifestFile(ctx)
        val dataDir = File(dataFiles)
        val portableDataPath = if (mf != null && mf.parentFile?.absolutePath == dataDir.absolutePath) "." else "Data Files"
        val out = Data(dataPath = portableDataPath, language = if (prefs.getString("pref_encoding", "win1252") == "win1251") "Russian" else "English")
        ModsCollection(ModType.Plugin, dataFiles, db).mods.filter { it.enabled }.sortedBy { it.order }.forEach { out.content.add(it.filename) }
        ModsCollection(ModType.Groundcover, dataFiles, db).mods.filter { it.enabled }.sortedBy { it.order }.forEach { out.groundcover.add(it.filename) }
        ModsCollection(ModType.Resource, dataFiles, db).mods.filter { it.enabled }.sortedBy { it.order }.forEach { out.archives.add(it.filename) }
        return out
    }

    fun writeFromDatabase(ctx: Context): Data {
        val out = collect(ctx)
        val f = manifestFile(ctx) ?: return out
        f.parentFile?.mkdirs()
        val text = buildString {
            append("[Build]\nformat=1\nname=").append(quote(out.name)).append('\n')
            append("data-path=").append(quote(out.dataPath)).append('\n')
            append("language=").append(quote(out.language)).append("\ncomplete=false\n\n")
            append("[Content]\n")
            out.content.forEach { append("content=").append(quote(it)).append('\n') }
            out.groundcover.forEach { append("groundcover=").append(quote(it)).append('\n') }
            append("\n[Archives]\n")
            out.archives.forEach { append("archive=").append(quote(it)).append('\n') }
        }
        f.writeText(text)
        return out
    }

    fun ensure(ctx: Context): Data = read(ctx) ?: writeFromDatabase(ctx)

    fun applyToDatabase(ctx: Context) {
        val m = read(ctx) ?: return
        val dataFiles = GameInstaller.getDataFiles(ctx)
        val db = ModsDatabaseOpenHelper.getInstance(ctx)
        fun apply(type: ModType, wanted: List<String>) {
            val c = ModsCollection(type, dataFiles, db)
            val order = wanted.mapIndexed { i, s -> s.toLowerCase() to i }.toMap()
            var tail = wanted.size
            c.mods.forEach { mod ->
                val pos = order[mod.filename.toLowerCase()]
                mod.enabled = pos != null
                mod.order = if (pos != null) pos + 1 else ++tail
                mod.dirty = true
            }
            c.mods.sortBy { it.order }
            c.update()
        }
        apply(ModType.Plugin, m.content)
        apply(ModType.Groundcover, m.groundcover)
        apply(ModType.Resource, m.archives)
    }
}
