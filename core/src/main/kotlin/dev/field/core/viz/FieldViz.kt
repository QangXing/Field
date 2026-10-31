package dev.field.core.viz

import dev.field.core.fields.FieldKind
import dev.field.core.fields.FieldModel
import dev.field.core.fields.FieldSample
import dev.field.core.fields.Magnet
import dev.field.core.fields.PointCharge
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

data class ArrowSample(
    val x: Double,
    val y: Double,
    val vx: Double,
    val vy: Double,
    val magnitude: Double,
)

/**
 * Row-major scalar grid, normalized to 0..1. Row 0 corresponds to [Aabb.minY].
 */
data class Heatmap(
    val cols: Int,
    val rows: Int,
    val values: FloatArray,
)

@Serializable
enum class HeatmapMode { POTENTIAL, MAGNITUDE }

object FieldViz {
    private const val MIN_DIRECTION_LENGTH = 1e-6
    private const val CHARGE_SEED_RADIUS = 0.75
    private const val MAGNET_SEED_RADIUS = 0.5
    private const val CHARGE_SEED_COUNT = 12
    private const val MAGNET_SEED_COUNT = 10
    private const val GRID_COLS = 6
    private const val GRID_ROWS = 4

    fun arrows(
        field: FieldModel,
        bounds: Aabb,
        cols: Int,
        rows: Int,
        kind: FieldKind,
    ): List<ArrowSample> {
        if (cols <= 0 || rows <= 0) return emptyList()
        val cellW = bounds.width / cols
        val cellH = bounds.height / rows
        val result = ArrayList<ArrowSample>(cols * rows)
        for (row in 0 until rows) {
            val y = bounds.minY + (row + 0.5) * cellH
            for (col in 0 until cols) {
                val x = bounds.minX + (col + 0.5) * cellW
                val vector = field.fieldVector(Vec2(x, y), kind)
                result.add(
                    ArrowSample(
                        x = x,
                        y = y,
                        vx = vector.x,
                        vy = vector.y,
                        magnitude = vector.length,
                    ),
                )
            }
        }
        return result
    }

    fun streamlines(
        field: FieldModel,
        kind: FieldKind,
        seeds: List<Vec2>,
        bounds: Aabb,
        maxSteps: Int = 240,
        step: Double = 0.15,
    ): List<List<Vec2>> {
        if (maxSteps <= 0 || step <= 0.0) return emptyList()
        val lines = ArrayList<List<Vec2>>(seeds.size)
        val visitCell = step * 0.5

        for (seed in seeds) {
            if (!bounds.contains(seed)) continue
            val points = ArrayList<Vec2>(maxSteps + 1)
            points.add(seed)
            val visited = HashSet<Long>()
            visited.add(cellKey(seed, visitCell))
            var current = seed

            var stepIndex = 0
            while (stepIndex < maxSteps) {
                val k1 = direction(field, kind, current) ?: break
                val k2 = direction(field, kind, current + k1 * (step * 0.5)) ?: break
                val k3 = direction(field, kind, current + k2 * (step * 0.5)) ?: break
                val k4 = direction(field, kind, current + k3 * step) ?: break

                val delta = (k1 + k2 * 2.0 + k3 * 2.0 + k4) * (step / 6.0)
                val next = current + delta
                if (!next.isFinite() || !bounds.contains(next)) break

                val key = cellKey(next, visitCell)
                if (!visited.add(key)) break

                points.add(next)
                current = next
                stepIndex++
            }
            lines.add(points)
        }
        return lines
    }

    fun heatmap(
        field: FieldModel,
        bounds: Aabb,
        cols: Int,
        rows: Int,
        mode: HeatmapMode,
    ): Heatmap {
        if (cols <= 0 || rows <= 0) {
            return Heatmap(cols.coerceAtLeast(0), rows.coerceAtLeast(0), FloatArray(0))
        }

        val sample = FieldSample()
        val raw = DoubleArray(cols * rows)
        val cellW = bounds.width / cols
        val cellH = bounds.height / rows
        var min = Double.POSITIVE_INFINITY
        var max = Double.NEGATIVE_INFINITY

        for (row in 0 until rows) {
            val y = bounds.minY + (row + 0.5) * cellH
            for (col in 0 until cols) {
                val x = bounds.minX + (col + 0.5) * cellW
                field.sampleInto(Vec2(x, y), sample)
                val value = when (mode) {
                    HeatmapMode.POTENTIAL -> sample.potential
                    HeatmapMode.MAGNITUDE ->
                        sample.electric.length +
                            kotlin.math.abs(sample.magneticBz) +
                            sample.gravity.length +
                            sample.wind.length
                }
                raw[row * cols + col] = value
                if (value < min) min = value
                if (value > max) max = value
            }
        }

        val values = FloatArray(cols * rows)
        val range = max - min
        if (range.isFinite() && range > 1e-12) {
            for (i in values.indices) {
                values[i] = ((raw[i] - min) / range).toFloat()
            }
        }
        return Heatmap(cols, rows, values)
    }

    fun defaultSeeds(
        field: FieldModel,
        kind: FieldKind,
        bounds: Aabb,
    ): List<Vec2> {
        val seeds = ArrayList<Vec2>()
        when (kind) {
            FieldKind.ELECTRIC -> {
                for (source in field.sources) {
                    if (source is PointCharge) {
                        for (i in 0 until CHARGE_SEED_COUNT) {
                            val angle = 2.0 * PI * i / CHARGE_SEED_COUNT
                            seeds.add(
                                clampToBounds(
                                    Vec2(
                                        source.position.x + cos(angle) * CHARGE_SEED_RADIUS,
                                        source.position.y + sin(angle) * CHARGE_SEED_RADIUS,
                                    ),
                                    bounds,
                                ),
                            )
                        }
                    }
                }
            }

            FieldKind.MAGNETIC -> {
                for (source in field.sources) {
                    if (source is Magnet) {
                        val northX = source.position.x + cos(source.angle) * Magnet.POLE_OFFSET
                        val northY = source.position.y + sin(source.angle) * Magnet.POLE_OFFSET
                        for (i in 0 until MAGNET_SEED_COUNT) {
                            val angle = 2.0 * PI * i / MAGNET_SEED_COUNT
                            seeds.add(
                                clampToBounds(
                                    Vec2(
                                        northX + cos(angle) * MAGNET_SEED_RADIUS,
                                        northY + sin(angle) * MAGNET_SEED_RADIUS,
                                    ),
                                    bounds,
                                ),
                            )
                        }
                    }
                }
            }

            FieldKind.GRAVITY, FieldKind.DRAG, FieldKind.UNIFORM_FORCE -> Unit
        }

        if (seeds.isEmpty()) {
            val cellW = bounds.width / GRID_COLS
            val cellH = bounds.height / GRID_ROWS
            for (row in 0 until GRID_ROWS) {
                val y = bounds.minY + (row + 0.5) * cellH
                for (col in 0 until GRID_COLS) {
                    seeds.add(Vec2(bounds.minX + (col + 0.5) * cellW, y))
                }
            }
        }
        return seeds
    }

    private fun direction(field: FieldModel, kind: FieldKind, p: Vec2): Vec2? {
        val v = field.fieldVector(p, kind)
        val length = v.length
        if (length < MIN_DIRECTION_LENGTH || !length.isFinite()) return null
        return v * (1.0 / length)
    }

    private fun clampToBounds(p: Vec2, bounds: Aabb): Vec2 = Vec2(
        p.x.coerceIn(bounds.minX, bounds.maxX),
        p.y.coerceIn(bounds.minY, bounds.maxY),
    )

    private fun cellKey(p: Vec2, cellSize: Double): Long {
        val cx = floor(p.x / cellSize).toLong()
        val cy = floor(p.y / cellSize).toLong()
        return (cx shl 32) xor (cy and 0xFFFF_FFFFL)
    }
}
