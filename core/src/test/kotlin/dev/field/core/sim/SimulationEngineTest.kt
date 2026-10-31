package dev.field.core.sim

import dev.field.core.fields.DragRegion
import dev.field.core.fields.FieldModel
import dev.field.core.fields.GravityZone
import dev.field.core.fields.Magnet
import dev.field.core.fields.PointCharge
import dev.field.core.fields.WallSegment
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimulationEngineTest {

    @Test
    fun lorentzForceIsPerpendicularToVelocity() {
        val engine = SimulationEngine(
            field = FieldModel(listOf(Magnet(1, Vec2.ZERO, 5.0, 0.0))),
            bounds = Aabb(-20.0, -20.0, 20.0, 20.0),
            settings = SimSettings(collisions = false),
        )
        val probe = Probe(1, Vec2(3.0, 0.0), Vec2(0.0, 2.0))
        val acceleration = engine.accelerationAt(probe, probe.position, probe.velocity)
        assertEquals(0.0, acceleration.dot(probe.velocity), 1e-9)
    }

    @Test
    fun magneticFieldDoesNoWork() {
        val engine = SimulationEngine(
            field = FieldModel(listOf(Magnet(1, Vec2.ZERO, 10.0, 0.0))),
            bounds = Aabb(-20.0, -20.0, 20.0, 20.0),
            settings = SimSettings(
                fixedDt = 1.0 / 120.0,
                subSteps = 1,
                integrator = Integrator.RK4,
                collisions = false,
            ),
            probes = mutableListOf(Probe(1, Vec2(3.0, 0.0), Vec2(0.0, 2.0))),
        )
        val probe = engine.probes[0]
        val initialSpeed = probe.velocity.length
        repeat(240) { engine.step() }
        assertEquals(initialSpeed, probe.velocity.length, 1e-6)
    }

    @Test
    fun dragReachesTerminalSpeed() {
        val engine = SimulationEngine(
            field = FieldModel(
                sources = listOf(
                    DragRegion(
                        id = 1,
                        min = Vec2(-50.0, -100.0),
                        max = Vec2(50.0, 100.0),
                        linear = 0.0,
                        quadratic = 1.0,
                    ),
                ),
                globalGravity = Vec2(0.0, -1.0),
            ),
            bounds = Aabb(-50.0, -100.0, 50.0, 100.0),
            settings = SimSettings(fixedDt = 0.01, subSteps = 1, collisions = false),
            probes = mutableListOf(Probe(1, Vec2.ZERO, Vec2.ZERO, mass = 1.0)),
        )
        val probe = engine.probes[0]
        repeat(2000) { engine.step() }

        // v_t = sqrt(m*g/c2) = 1.0 for m=1, g=1, c2=1.
        assertEquals(1.0, probe.velocity.length, 0.02)
    }

    @Test
    fun reflectBoundaryKeepsProbeInside() {
        val engine = SimulationEngine(
            field = FieldModel(emptyList()),
            bounds = Aabb(-5.0, -5.0, 5.0, 5.0),
            boundary = Boundary.REFLECT,
            settings = SimSettings(fixedDt = 0.1, subSteps = 1, collisions = false),
            probes = mutableListOf(Probe(1, Vec2(4.9, 0.0), Vec2(10.0, 0.0))),
        )
        val probe = engine.probes[0]
        engine.step()

        assertTrue(probe.position.x <= 5.0 + 1e-12)
        assertTrue(probe.velocity.x < 0.0)
    }

    @Test
    fun wrapBoundaryTeleportsProbe() {
        val engine = SimulationEngine(
            field = FieldModel(emptyList()),
            bounds = Aabb(-5.0, -5.0, 5.0, 5.0),
            boundary = Boundary.WRAP,
            settings = SimSettings(fixedDt = 0.1, subSteps = 1, collisions = false),
            probes = mutableListOf(Probe(1, Vec2(4.9, 0.0), Vec2(10.0, 0.0))),
        )
        val probe = engine.probes[0]
        engine.step()

        assertEquals(-4.1, probe.position.x, 1e-9)
        assertEquals(10.0, probe.velocity.x, 1e-12)
    }

    @Test
    fun openBoundaryRemovesEscapedProbes() {
        val engine = SimulationEngine(
            field = FieldModel(emptyList()),
            bounds = Aabb(-5.0, -5.0, 5.0, 5.0),
            boundary = Boundary.OPEN,
            settings = SimSettings(fixedDt = 0.1, subSteps = 1, collisions = false),
            probes = mutableListOf(Probe(1, Vec2(10.0, 0.0), Vec2.ZERO)),
        )
        engine.step()

        assertTrue(engine.probes.isEmpty())
    }

    @Test
    fun simulationIsDeterministic() {
        fun build(): SimulationEngine {
            val sources = listOf(
                PointCharge(1, Vec2(-3.0, 0.0), 2.0),
                PointCharge(2, Vec2(3.0, 0.0), -2.0),
                Magnet(3, Vec2(0.0, 4.0), 1.5, 0.4),
                GravityZone(4, Vec2(-6.0, -6.0), Vec2(6.0, 6.0), Vec2(0.0, -1.0)),
                DragRegion(5, Vec2(4.0, -6.0), Vec2(10.0, 6.0), 0.4, 0.2, Vec2(0.5, 0.0)),
            )
            val probes = mutableListOf(
                Probe(1, Vec2(-1.0, 1.0), Vec2(1.0, 0.5)),
                Probe(2, Vec2(0.0, -2.0), Vec2(-0.5, 1.0,)),
                Probe(3, Vec2(2.0, 3.0), Vec2(0.0, -1.0), charge = -1.0),
                Probe(4, Vec2(5.0, -1.0), Vec2(1.0, 1.0), mass = 2.0),
            )
            return SimulationEngine(
                field = FieldModel(sources),
                bounds = Aabb(-20.0, -12.0, 20.0, 12.0),
                boundary = Boundary.REFLECT,
                settings = SimSettings(fixedDt = 1.0 / 120.0, subSteps = 2, collisions = true),
                probes = probes,
            )
        }

        val a = build()
        val b = build()
        repeat(600) {
            a.step()
            b.step()
        }

        assertEquals(a.probes.size, b.probes.size)
        for (i in a.probes.indices) {
            assertEquals(a.probes[i].position.x, b.probes[i].position.x, 1e-9)
            assertEquals(a.probes[i].position.y, b.probes[i].position.y, 1e-9)
            assertEquals(a.probes[i].velocity.x, b.probes[i].velocity.x, 1e-9)
            assertEquals(a.probes[i].velocity.y, b.probes[i].velocity.y, 1e-9)
        }
    }

    @Test
    fun collisionsKeepProbesApart() {
        val engine = SimulationEngine(
            field = FieldModel(emptyList()),
            bounds = Aabb(-10.0, -10.0, 10.0, 10.0),
            settings = SimSettings(fixedDt = 0.01, subSteps = 2, collisions = true),
            probes = mutableListOf(
                Probe(1, Vec2(0.0, 0.0), Vec2.ZERO),
                Probe(2, Vec2(0.1, 0.0), Vec2.ZERO),
            ),
        )
        repeat(5) { engine.step() }

        val distance = (engine.probes[1].position - engine.probes[0].position).length
        assertTrue(distance >= 0.2399)
    }

    @Test
    fun wallCollisionPushesProbeOut() {
        val wall = WallSegment(1, Vec2(-3.0, -2.0), Vec2(3.0, -2.0), thickness = 0.2)
        val engine = SimulationEngine(
            field = FieldModel(listOf(wall)),
            bounds = Aabb(-10.0, -10.0, 10.0, 10.0),
            settings = SimSettings(fixedDt = 0.05, subSteps = 1, collisions = true),
            probes = mutableListOf(Probe(1, Vec2(0.0, -1.9), Vec2(0.0, -2.0))),
        )
        val probe = engine.probes[0]
        engine.step()

        assertEquals(-1.78, probe.position.y, 1e-6)
        assertTrue(probe.velocity.y > 0.0)
    }

    @Test
    fun nonFiniteStateFreezesProbe() {
        val engine = SimulationEngine(
            field = FieldModel(emptyList()),
            bounds = Aabb(-5.0, -5.0, 5.0, 5.0),
            settings = SimSettings(collisions = false),
            probes = mutableListOf(Probe(1, Vec2(1.0, 2.0), Vec2(Double.NaN, 0.0))),
        )
        val probe = engine.probes[0]
        engine.step()

        assertTrue(probe.faulted)
        assertEquals(0.0, probe.velocity.length, 1e-12)
        assertTrue(probe.position.isFinite())
    }

    @Test
    fun speedIsClamped() {
        val engine = SimulationEngine(
            field = FieldModel(emptyList()),
            bounds = Aabb(-500.0, -500.0, 500.0, 500.0),
            settings = SimSettings(fixedDt = 1.0 / 60.0, collisions = false),
            probes = mutableListOf(Probe(1, Vec2.ZERO, Vec2(1000.0, 0.0))),
        )
        engine.step()

        assertEquals(60.0, engine.probes[0].velocity.length, 1e-9)
    }

    @Test
    fun launchCreatesRequestedProbes() {
        val engine = SimulationEngine(
            field = FieldModel(emptyList()),
            bounds = Aabb(-20.0, -20.0, 20.0, 20.0),
            settings = SimSettings(collisions = false),
        )
        val rng = Random(42)
        engine.launch(
            count = 10,
            origin = Vec2(1.0, 1.0),
            direction = Vec2(1.0, 0.0),
            speed = 3.0,
            charge = 1.0,
            spreadRadians = 0.3,
            rng = rng,
        )

        assertEquals(10, engine.probes.size)
        for (probe in engine.probes) {
            assertEquals(3.0, probe.velocity.length, 1e-9)
            assertEquals(1.0, probe.position.x, 1e-12)
        }
        assertFalse(engine.probes.map { it.id }.toSet().size != engine.probes.size)
    }
}
