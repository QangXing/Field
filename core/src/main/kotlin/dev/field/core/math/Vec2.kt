package dev.field.core.math

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2D vector in world units. Serialized as a compact `[x, y]` JSON array.
 */
@Serializable(with = Vec2ArraySerializer::class)
data class Vec2(val x: Double, val y: Double) {

    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    operator fun times(scalar: Double): Vec2 = Vec2(x * scalar, y * scalar)

    operator fun div(scalar: Double): Vec2 = Vec2(x / scalar, y / scalar)

    operator fun unaryMinus(): Vec2 = Vec2(-x, -y)

    val lengthSquared: Double
        get() = x * x + y * y

    val length: Double
        get() = sqrt(lengthSquared)

    fun normalized(): Vec2 {
        val len = length
        return if (len > 1e-12 && len.isFinite()) Vec2(x / len, y / len) else ZERO
    }

    fun dot(other: Vec2): Double = x * other.x + y * other.y

    /** Counter-clockwise perpendicular: `(-y, x)`. */
    fun perp(): Vec2 = Vec2(-y, x)

    fun rotate(radians: Double): Vec2 {
        val c = cos(radians)
        val s = sin(radians)
        return Vec2(x * c - y * s, x * s + y * c)
    }

    fun isFinite(): Boolean = x.isFinite() && y.isFinite()

    companion object {
        val ZERO: Vec2 = Vec2(0.0, 0.0)
    }
}

object Vec2ArraySerializer : KSerializer<Vec2> {
    private val delegate = ListSerializer(Double.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Vec2) {
        encoder.encodeSerializableValue(delegate, listOf(value.x, value.y))
    }

    override fun deserialize(decoder: Decoder): Vec2 {
        val values = decoder.decodeSerializableValue(delegate)
        require(values.size == 2) { "Vec2 必须编码为 [x, y]" }
        return Vec2(values[0], values[1])
    }
}
