package dev.field.core.scene

import kotlinx.serialization.json.Json

data class SceneError(
    val path: String,
    val message: String,
)

sealed class SceneParseResult {
    data class Ok(val scene: Scene) : SceneParseResult()
    data class Err(val errors: List<SceneError>) : SceneParseResult()
}

class SceneCodec {
    private val format = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(scene: Scene): String = format.encodeToString(Scene.serializer(), scene)

    fun decode(json: String): SceneParseResult {
        val scene = try {
            format.decodeFromString(Scene.serializer(), json)
        } catch (error: Exception) {
            return SceneParseResult.Err(
                listOf(SceneError("$", error.message ?: "JSON 解析失败")),
            )
        }

        val errors = SceneValidator.validate(scene)
        return if (errors.isEmpty()) {
            SceneParseResult.Ok(scene)
        } else {
            SceneParseResult.Err(errors)
        }
    }
}
