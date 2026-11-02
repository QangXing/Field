package dev.field.core.fields

import dev.field.core.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class FieldMathTest {

    @Test
    fun electricFieldIsUniformInsideFrame() {
        val model = FieldModel(listOf(PointCharge(1, Vec2.ZERO, 2.0, extent = 2.0)))
        val inside = model.sample(Vec2(1.0, 0.0))
        assertEquals(2.0, inside.electric.x, 1e-12)
        assertEquals(0.0, inside.electric.y, 1e-12)

        val outside = model.sample(Vec2(2.5, 0.0))
        assertEquals(0.0, outside.electric.length, 1e-12)
    }

    @Test
    fun electricFieldDirectionFollowsAngleAndSign() {
        val angled = FieldModel(
            listOf(PointCharge(1, Vec2.ZERO, 2.0, extent = 2.0, angle = PI / 2.0)),
        )
        val v = angled.sample(Vec2(0.0, 1.0)).electric
        assertEquals(0.0, v.x, 1e-12)
        assertEquals(2.0, v.y, 1e-12)

        val reversed = FieldModel(
            listOf(PointCharge(1, Vec2.ZERO, -2.0, extent = 2.0)),
        )
        assertEquals(-2.0, reversed.sample(Vec2(1.0, 0.0)).electric.x, 1e-12)
    }

    @Test
    fun electricFieldScalesWithFieldConstants() {
        val source = PointCharge(1, Vec2.ZERO, 2.0, extent = 2.0)
        val base = FieldModel(listOf(source), kE = 1.0)
        val doubled = FieldModel(listOf(source), kE = 2.0)
        val p = Vec2(1.0, 0.0)
        assertEquals(base.sample(p).electric.x * 2.0, doubled.sample(p).electric.x, 1e-9)
    }

    @Test
    fun uniformElectricPotentialIsLinear() {
        val model = FieldModel(listOf(PointCharge(1, Vec2.ZERO, 2.0, extent = 5.0)))
        // phi = -E·r, E = (2, 0)
        assertEquals(-4.0, model.potential(Vec2(2.0, 0.0)), 1e-9)
        assertEquals(0.0, model.potential(Vec2.ZERO), 1e-9)
        // Outside the frame the field contributes nothing.
        assertEquals(0.0, model.potential(Vec2(6.0, 0.0)), 1e-9)
    }

    @Test
    fun magneticFieldIsUniformOutOfPlane() {
        val model = FieldModel(listOf(Magnet(1, Vec2.ZERO, 1.5, extent = 2.0)))
        assertEquals(1.5, model.sample(Vec2(1.0, 1.0)).magneticBz, 1e-12)
        assertEquals(0.0, model.sample(Vec2(2.5, 0.0)).magneticBz, 1e-12)

        val reversed = FieldModel(listOf(Magnet(1, Vec2.ZERO, -1.5, extent = 2.0)))
        assertEquals(-1.5, reversed.sample(Vec2.ZERO).magneticBz, 1e-12)
    }

    @Test
    fun magneticFieldHasNoInPlaneArrowDirection() {
        val model = FieldModel(listOf(Magnet(1, Vec2.ZERO, 1.0, extent = 4.0)))
        assertEquals(Vec2.ZERO, model.fieldVector(Vec2(1.0, 0.0), FieldKind.MAGNETIC))
    }

    @Test
    fun circularElectricFrameClipsCorners() {
        val model = FieldModel(
            listOf(
                PointCharge(1, Vec2.ZERO, 2.0, extent = 2.0, shape = FieldShape.CIRCLE),
            ),
        )
        assertEquals(2.0, model.sample(Vec2(2.0, 0.0)).electric.x, 1e-12)
        // Inside the square but outside the inscribed circle.
        assertEquals(0.0, model.sample(Vec2(1.5, 1.5)).electric.length, 1e-12)
    }

    @Test
    fun circularZoneUsesInscribedCircle() {
        val model = FieldModel(
            listOf(
                GravityZone(
                    id = 1,
                    min = Vec2(-2.0, -2.0),
                    max = Vec2(2.0, 2.0),
                    gravity = Vec2(0.0, -3.0),
                    shape = FieldShape.CIRCLE,
                ),
            ),
        )
        assertEquals(-3.0, model.sample(Vec2(1.0, 0.0)).gravity.y, 1e-12)
        assertEquals(0.0, model.sample(Vec2(1.7, 1.7)).gravity.length, 1e-12)
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
