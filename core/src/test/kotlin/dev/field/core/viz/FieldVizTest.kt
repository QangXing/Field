package dev.field.core.viz

import dev.field.core.fields.FieldKind
import dev.field.core.fields.FieldModel
import dev.field.core.fields.GravityZone
import dev.field.core.fields.Magnet
import dev.field.core.fields.PointCharge
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldVizTest {

    private val bounds = Aabb(-5.0, -5.0, 5.0, 5.0)

    @Test
    fun arrowsSampleCellCenters() {
        val field = FieldModel(listOf(PointCharge(1, Vec2.ZERO, 2.0)))
        val arrows = FieldViz.arrows(field, bounds, cols = 8, rows = 6, kind = FieldKind.ELECTRIC)

        assertEquals(48, arrows.size)
        val first = arrows.first()
        assertEquals(-5.0 + 0.5 * (10.0 / 8.0), first.x, 1e-12)
        assertEquals(-5.0 + 0.5 * (10.0 / 6.0), first.y, 1e-12)
        assertTrue(arrows.all { it.magnitude.isFinite() && it.magnitude >= 0.0 })
    }

    @Test
    fun heatmapHasNormalizedGrid() {
        val field = FieldModel(listOf(PointCharge(1, Vec2(0.0, -5.0), 2.0)))
        val heatmap = FieldViz.heatmap(field, bounds, cols = 8, rows = 6, mode = HeatmapMode.POTENTIAL)

        assertEquals(8, heatmap.cols)
        assertEquals(6, heatmap.rows)
        assertEquals(48, heatmap.values.size)
        assertTrue(heatmap.values.all { it >= 0f && it <= 1f })

        // Row 0 is at minY, closest to the charge, so it must be brighter than the top row.
        val bottomAverage = (0 until 8).sumOf { heatmap.values[it].toDouble() } / 8.0
        val topAverage = (40 until 48).sumOf { heatmap.values[it].toDouble() } / 8.0
        assertTrue(bottomAverage > topAverage)
    }

    @Test
    fun magneticHeatmapUsesMagnitudeMode() {
        val field = FieldModel(listOf(Magnet(1, Vec2.ZERO, 1.0, 0.0)))
        val heatmap = FieldViz.heatmap(field, bounds, cols = 4, rows = 4, mode = HeatmapMode.MAGNITUDE)
        assertEquals(16, heatmap.values.size)
        assertTrue(heatmap.values.all { it >= 0f && it <= 1f })
    }

    @Test
    fun streamlinesFollowUniformGravity() {
        val field = FieldModel(
            listOf(GravityZone(1, Vec2(-10.0, -10.0), Vec2(10.0, 10.0), Vec2(0.0, -1.0))),
        )
        val lines = FieldViz.streamlines(
            field = field,
            kind = FieldKind.GRAVITY,
            seeds = listOf(Vec2(0.0, 5.0)),
            bounds = Aabb(-10.0, -10.0, 10.0, 10.0),
            maxSteps = 60,
            step = 0.2,
        )

        assertEquals(1, lines.size)
        assertTrue(lines[0].size > 5)
        val last = lines[0].last()
        assertTrue(last.y < 5.0)
        assertTrue(last.y >= -10.0)
    }

    @Test
    fun defaultSeedsDependOnFieldKind() {
        val chargeField = FieldModel(listOf(PointCharge(1, Vec2.ZERO, 1.0)))
        assertEquals(12, FieldViz.defaultSeeds(chargeField, FieldKind.ELECTRIC, bounds).size)

        val magnetField = FieldModel(listOf(Magnet(1, Vec2.ZERO, 1.0, 0.0)))
        assertEquals(10, FieldViz.defaultSeeds(magnetField, FieldKind.MAGNETIC, bounds).size)

        val emptyField = FieldModel(emptyList())
        assertEquals(24, FieldViz.defaultSeeds(emptyField, FieldKind.GRAVITY, bounds).size)
    }
}
