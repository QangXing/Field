package dev.field.core.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathTest {

    @Test
    fun vectorAlgebra() {
        val a = Vec2(3.0, 4.0)
        val b = Vec2(-1.0, 2.0)
        assertEquals(5.0, a.length, 1e-12)
        assertEquals(25.0, a.lengthSquared, 1e-12)
        assertEquals(Vec2(2.0, 6.0), a + b)
        assertEquals(Vec2(4.0, 2.0), a - b)
        assertEquals(Vec2(6.0, 8.0), a * 2.0)
        assertEquals(Vec2(1.5, 2.0), a / 2.0)
        assertEquals(5.0, a.dot(b), 1e-12)
        assertEquals(Vec2(-4.0, 3.0), a.perp())
    }

    @Test
    fun normalizationAndRotation() {
        assertEquals(Vec2(0.6, 0.8), Vec2(3.0, 4.0).normalized())
        assertEquals(Vec2.ZERO, Vec2.ZERO.normalized())

        val rotated = Vec2(1.0, 0.0).rotate(Math.PI / 2.0)
        assertEquals(0.0, rotated.x, 1e-12)
        assertEquals(1.0, rotated.y, 1e-12)
    }

    @Test
    fun nonFiniteIsDetected() {
        assertTrue(Vec2(1.0, 2.0).isFinite())
        assertFalse(Vec2(Double.NaN, 2.0).isFinite())
        assertFalse(Vec2(1.0, Double.POSITIVE_INFINITY).isFinite())
    }

    @Test
    fun aabbHelpers() {
        val box = Aabb.fromCenter(0.0, 0.0, 2.0, 1.0)
        assertEquals(-2.0, box.minX, 1e-12)
        assertEquals(1.0, box.maxY, 1e-12)
        assertEquals(4.0, box.width, 1e-12)
        assertEquals(2.0, box.height, 1e-12)
        assertEquals(Vec2.ZERO, box.center)
        assertTrue(box.contains(Vec2(1.9, 0.9)))
        assertFalse(box.contains(Vec2(2.1, 0.0)))
        assertTrue(box.intersects(Aabb(1.0, 0.0, 3.0, 2.0)))
        assertFalse(box.intersects(Aabb(3.0, 0.0, 5.0, 2.0)))

        val expanded = box.expand(0.5)
        assertEquals(-2.5, expanded.minX, 1e-12)
        assertEquals(2.5, expanded.maxX, 1e-12)
    }
}
