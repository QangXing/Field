package dev.field.core.math

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Axis-aligned bounding box. Serialized as `[minX, minY, maxX, maxY]`.
 */
@Serializable(with = AabbArraySerializer::class)
data class Aabb(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {
    val width: Double
        get() = maxX - minX

    val height: Double
        get() = maxY - minY

    val center: Vec2
        get() = Vec2((minX + maxX) * 0.5, (minY + maxY) * 0.5)

    fun contains(p: Vec2): Boolean =
        p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY

    fun intersects(other: Aabb): Boolean =
        minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY

    fun expand(margin: Double): Aabb = Aabb(
        minX = minX - margin,
        minY = minY - margin,
        maxX = maxX + margin,
        maxY = maxY + margin,
    )

    companion object {
        fun fromCenter(cx: Double, cy: Double, halfW: Double, halfH: Double): Aabb = Aabb(
            minX = cx - halfW,
            minY = cy - halfH,
            maxX = cx + halfW,
            maxY = cy + halfH,
        )
    }
}

object AabbArraySerializer : KSerializer<Aabb> {
    private val delegate = ListSerializer(Double.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Aabb) {
        encoder.encodeSerializableValue(
            delegate,
            listOf(value.minX, value.minY, value.maxX, value.maxY),
        )
    }

    override fun deserialize(decoder: Decoder): Aabb {
        val values = decoder.decodeSerializableValue(delegate)
        require(values.size == 4) { "Aabb 必须编码为 [minX, minY, maxX, maxY]" }
        return Aabb(values[0], values[1], values[2], values[3])
    }
}
