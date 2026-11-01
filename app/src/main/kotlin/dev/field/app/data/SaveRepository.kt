package dev.field.app.data

import android.content.Context
import android.net.Uri
import dev.field.core.scene.Scene
import dev.field.core.scene.SceneCodec
import dev.field.core.scene.SceneParseResult
import java.io.File

/**
 * File-backed scene storage under `filesDir/saves`, plus SAF import/export.
 * All operations are synchronous and small; callers invoke them off the UI thread only
 * for explicit import/export if needed.
 */
class SaveRepository(private val context: Context) {

    private val codec = SceneCodec()
    private val dir: File = File(context.filesDir, "saves").apply { mkdirs() }

    data class Entry(val file: File, val name: String, val modified: Long)

    val autosaveFile: File get() = File(dir, AUTOSAVE_NAME)

    fun list(): List<Entry> = dir.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.name.endsWith(SCENE_SUFFIX) && it.name != AUTOSAVE_NAME }
        ?.map { Entry(it, it.name.removeSuffix(SCENE_SUFFIX), it.lastModified()) }
        ?.sortedByDescending { it.modified }
        ?.toList()
        ?: emptyList()

    fun save(scene: Scene): Result<File> = runCatching {
        val file = File(dir, safeName(scene.name) + SCENE_SUFFIX)
        file.writeText(codec.encode(scene))
        file
    }

    fun saveAutosave(scene: Scene): Result<File> = runCatching {
        val file = File(dir, AUTOSAVE_NAME)
        file.writeText(codec.encode(scene))
        file
    }

    fun load(file: File): Result<Scene> = runCatching {
        decode(codec, file.readText())
    }

    fun loadAutosave(): Scene? = runCatching {
        val file = File(dir, AUTOSAVE_NAME)
        if (!file.exists()) null else decode(codec, file.readText())
    }.getOrNull()

    fun delete(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    /** Renames a save (both the scene name and the backing file). */
    fun rename(file: File, newName: String): Result<File> = runCatching {
        val scene = load(file).getOrThrow()
        val target = File(dir, safeName(newName) + SCENE_SUFFIX)
        target.writeText(codec.encode(scene.copy(name = newName.trim().ifEmpty { "未命名世界" })))
        if (target.absolutePath != file.absolutePath) {
            file.delete()
        }
        target
    }

    /** Applies [transform] to a stored scene and writes it back. */
    fun update(file: File, transform: (Scene) -> Scene): Result<Scene> = runCatching {
        val scene = transform(load(file).getOrThrow())
        file.writeText(codec.encode(scene))
        scene
    }

    fun fileForName(name: String): File = File(dir, safeName(name) + SCENE_SUFFIX)

    fun loadNamed(name: String): Scene? = runCatching {
        val file = File(dir, name)
        if (!file.exists()) null else decode(codec, file.readText())
    }.getOrNull()

    fun export(scene: Scene, uri: Uri): Result<Unit> = runCatching {
        val payload = codec.encode(scene).toByteArray()
        context.contentResolver.openOutputStream(uri)?.use { it.write(payload) }
            ?: error("无法打开所选位置")
    }

    fun import(uri: Uri): Result<Scene> = runCatching {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().decodeToString() }
            ?: error("无法读取所选文件")
        decode(codec, text)
    }

    private fun decode(codec: SceneCodec, json: String): Scene =
        when (val result = codec.decode(json)) {
            is SceneParseResult.Ok -> result.scene
            is SceneParseResult.Err -> error(
                result.errors.joinToString("；") { "${it.path}: ${it.message}" },
            )
        }

    fun safeName(raw: String): String {
        val trimmed = raw.trim().ifEmpty { "未命名世界" }
        val cleaned = trimmed.map { ch ->
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == ' ') ch else '_'
        }.joinToString("")
        return cleaned.take(40).ifEmpty { "未命名世界" }
    }

    companion object {
        const val SCENE_SUFFIX = ".field.json"
        const val AUTOSAVE_NAME = "autosave.field.json"
    }
}
