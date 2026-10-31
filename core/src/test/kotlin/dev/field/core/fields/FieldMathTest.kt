package dev.field.core.fields

import dev.field.core.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

class FieldMathTest {

    private val eps = FieldSource.DEFAULT_SOFTENING

    @Test
    fun pointChargeFollowsSoftenedInverseSquare() {
        val model = FieldModel(listOf(PointCharge(1, Vec2.ZERO, 2.0)))
        val r = 2.0
        val expectedX = 2.0 * r / (r * r + eps * eps).pow(1.5)

        val sample = model.sample(Vec2(r, 0.0))
        assertEquals(expectedX, sample.electric.x, 1e-9)
        assertEquals(0.0, sample.electric.y, 1e-12)

        // Far from the singularity this approaches kE*q/r^2.
        val unsoftened = 2.0 / (r * r)
        assertEquals(unsoftened, sample.electric.length, unsoftened * 0.01)
    }

    @Test
    fun pointChargeScalesWithFieldConstants() {
        val source = PointCharge(1, Vec2.ZERO, 2.0)
        val base = FieldModel(listOf(source), kE = 1.0)
        val doubled = FieldModel(listOf(source), kE = 2.0)
        val p = Vec2(2.0, 0.0)
        assertEquals(base.sample(p).electric.x * 2.0, doubled.sample(p).electric.x, 1e-9)
    }

    @Test
    fun potentialMatchesCharge() {
        val model = FieldModel(listOf(PointCharge(1, Vec2.ZERO, 2.0)))
        val expected = 2.0 / sqrt(4.0 + eps * eps)
        assertEquals(expected, model.potential(Vec2(2.0, 0.0)), 1e-9)
    }

    @Test
    fun magneticDipoleIsAntisymmetric() {
        val model = FieldModel(listOf(Magnet(1, Vec2.ZERO, 1.5, 0.0)))
        val points = listOf(Vec2(1.0, 2.0), Vec2(-3.0, 1.0), Vec2(0.5, 0.7))
        for (p in points) {
            val mirrored = Vec2(-p.x, -p.y)
            assertEquals(
                -model.sample(p).magneticBz,
                model.sample(mirrored).magneticBz,
                1e-9,
            )
        }
        // The two poles cancel exactly at the magnet center.
        assertEquals(0.0, model.sample(Vec2.ZERO).magneticBz, 1e-12)
    }

    @Test
    fun magneticFieldVectorFollowsBzContours() {
        val model = FieldModel(listOf(Magnet(1, Vec2.ZERO, 1.0, 0.0)))
        val v = model.fieldVector(Vec2(1.0, 0.0), FieldKind.MAGNETIC)
        // d(Bz)/dy = 0 on the axis, so the contour direction is along +/-y.
        assertEquals(0.0, v.x, 1e-3)
        assertTrue(kotlin.math.abs(v.y) > 0.0)
    }

    @Test
    fun dragFieldVectorReturnsAmbientWind() {
        val model = FieldModel(
            listOf(
                DragRegion(
                    id = 1,
                    min = Vec2(-5.0, -5.0),
                    max = Vec2(5.0, 5.0),
                    linear = 0.0,
                    quadratic = 0.0,
                    wind = Vec2(2.0, 0.0),
                ),
            ),
        )
        assertEquals(2.0, model.fieldVector(Vec2.ZERO, FieldKind.DRAG).x, 1e-12)
        assertEquals(0.0, model.fieldVector(Vec2(10.0, 0.0), FieldKind.DRAG).x, 1e-12)
    }

    @Test
    fun uniformForceSumsZones() {
        val model = FieldModel(
            listOf(
                ForceZone(1, Vec2(-5.0, -5.0), Vec2(5.0, 5.0), Vec2(1.0, 0.0)),
                ForceZone(2, Vec2(0.0, -5.0), Vec2(5.0, 5.0), Vec2(0.0, 1.0)),
            ),
        )
        assertEquals(Vec2(1.0, 1.0), model.fieldVector(Vec2(1.0, 1.0), FieldKind.UNIFORM_FORCE))
        assertEquals(Vec2(1.0, 0.0), model.fieldVector(Vec2(-2.0, 0.0), FieldKind.UNIFORM_FORCE))
        assertEquals(Vec2.ZERO, model.fieldVector(Vec2(6.0, 0.0), FieldKind.UNIFORM_FORCE))
    }

    @Test
    fun globalGravityIsAddedAfterGravitySources() {
        val model = FieldModel(
            sources = listOf(MassSource(1, Vec2.ZERO, 1.0)),
            globalGravity = Vec2(0.0, -2.0),
        )
        val sample = model.sample(Vec2(1.0, 0.0))
        assertTrue(sample.gravity.x > 0.0)
        assertEquals(-2.0, sample.gravity.y, 1e-6)
    }
}
