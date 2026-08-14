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
        var formatVersion: Int = 1,
        var name: String = "ArenaMW",
        var dataPath: String = "",
        var language: String = "English",
        var complete: Boolean = false,
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
        return when {
            canonical.exists() -> canonical
            nested.exists() -> nested
            else -> canonical
        }
    }

    private fun unquote(v: String): String {
        val t = v.trim()
        if (t.length < 2 || t.first() != '"' || t.last() != '"') return t
        val src = t.substring(1, t.length - 1)
        val out = StringBuilder()
        var escaped = false
        src.forEach { ch ->
            if (escaped) {
                out.append(when (ch) { 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; else -> ch })
                escaped = false
            } else if (ch == '\\') escaped = true else out.append(ch)
        }
        if (escaped) out.append('\\')
        return out.toString()
    }

    private fun quote(v: String): String {
        val out = StringBuilder("\"")
        v.forEach { ch ->
            when (ch) {
                '\\', '"' -> out.append('\\').append(ch)
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.append('"').toString()
    }

    private fun parseBool(v: String): Boolean {
        val s = v.trim().toLowerCase()
        return s == "true" || s == "1" || s == "yes"
    }

    private fun isBuildSection(section: String): Boolean =
        section.isEmpty() || section == "build" || section == "general" || section == "manifest"

    private fun canonicalLanguage(v: String): String {
        return when (v.trim().toLowerCase()) {
            "", "english", "английский", "anglais" -> "English"
            "french", "французский", "français" -> "French"
            "german", "немецкий", "deutsch" -> "German"
            "italian", "итальянский", "italiano" -> "Italian"
            "polish", "польский", "polski" -> "Polish"
            "russian", "русский", "русский язык" -> "Russian"
            "spanish", "испанский", "español" -> "Spanish"
            else -> v.trim()
        }
    }

    fun read(ctx: Context): Data? {
        val f = manifestFile(ctx) ?: return null
        if (!f.exists()) return null
        val out = Data()
        var section = ""
        f.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEachLine
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim().toLowerCase()
                return@forEachLine
            }
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEachLine
            val key = line.substring(0, eq).trim().toLowerCase()
            val value = unquote(line.substring(eq + 1))

            when {
                isBuildSection(section) && (key == "format" || key == "version") ->
                    value.toIntOrNull()?.takeIf { it > 0 }?.let { out.formatVersion = it }
                isBuildSection(section) && (key == "name" || key == "build-name") ->
                    out.name = value
                isBuildSection(section) && (key == "data" || key == "data-path" || key == "datafiles") ->
                    out.dataPath = value
                key == "language" || key == "locale"
                    || ((section == "language" || section == "locale")
                        && (key == "value" || key == "name" || key == "selected" || key == "current")) ->
                    out.language = canonicalLanguage(value)
                isBuildSection(section) && (key == "complete" || key == "locked" || key == "read-only") ->
                    out.complete = parseBool(value)
                key == "content" || key == "plugin" || key == "esm" || key == "esp"
                    || key == "omwgame" || key == "omwaddon" -> out.content.add(value)
                key == "groundcover" || key == "grass" -> out.groundcover.add(value)
                key == "archive" || key == "bsa" || key == "fallback-archive" -> out.archives.add(value)
            }
            // Unknown and network-only keys are intentionally ignored, as on desktop.
        }
        if (out.name.isBlank()) out.name = "ArenaMW"
        out.language = canonicalLanguage(out.language)
        return out
    }

    private fun collect(ctx: Context, existing: Data?): Data {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val dataFiles = GameInstaller.getDataFiles(ctx)
        val db = ModsDatabaseOpenHelper.getInstance(ctx)
        val mf = manifestFile(ctx)
        val dataDir = File(dataFiles)
        val defaultPortablePath = if (mf != null && mf.parentFile?.absolutePath == dataDir.absolutePath) "." else "Data Files"
        val encodingLanguage = if (prefs.getString("pref_encoding", "win1252") == "win1251") "Russian" else "English"

        val out = Data(
            formatVersion = existing?.formatVersion?.takeIf { it > 0 } ?: 1,
            name = existing?.name?.takeIf { it.isNotBlank() } ?: "ArenaMW",
            dataPath = existing?.dataPath?.takeIf { it.isNotBlank() } ?: defaultPortablePath,
            language = canonicalLanguage(existing?.language?.takeIf { it.isNotBlank() } ?: encodingLanguage),
            complete = existing?.complete ?: false
        )
        ModsCollection(ModType.Plugin, dataFiles, db).mods.filter { it.enabled }.sortedBy { it.order }
            .forEach { out.content.add(it.filename) }
        ModsCollection(ModType.Groundcover, dataFiles, db).mods.filter { it.enabled }.sortedBy { it.order }
            .forEach { out.groundcover.add(it.filename) }
        ModsCollection(ModType.Resource, dataFiles, db).mods.filter { it.enabled }.sortedBy { it.order }
            .forEach { out.archives.add(it.filename) }
        return out
    }

    fun writeFromDatabase(ctx: Context): Data {
        val existing = read(ctx)
        val out = collect(ctx, existing)
        val f = manifestFile(ctx) ?: return out
        f.parentFile?.mkdirs()
        val text = buildString {
            append("# ArenaMW standalone portable build manifest\n")
            append("# Ordered entries are applied exactly as written.\n\n")
            append("[Build]\n")
            append("format=").append(if (out.formatVersion > 0) out.formatVersion else 1).append('\n')
            append("name=").append(quote(out.name)).append('\n')
            append("data-path=").append(quote(out.dataPath)).append('\n')
            append("language=").append(quote(canonicalLanguage(out.language))).append('\n')
            append("complete=").append(if (out.complete) "true" else "false").append("\n\n")
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
            val order = wanted.mapIndexed { i, value -> value.toLowerCase() to i }.toMap()
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
