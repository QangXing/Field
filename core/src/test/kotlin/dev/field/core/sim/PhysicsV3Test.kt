package dev.field.core.sim

import dev.field.core.fields.FieldModel
import dev.field.core.fields.ForceZone
import dev.field.core.fields.MassSource
import dev.field.core.fields.PointCharge
import dev.field.core.fields.WallSegment
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsV3Test {

    @Test
    fun pointChargeExtentClipsField() {
        val model = FieldModel(listOf(PointCharge(1, Vec2.ZERO, 1.0, extent = 2.0)))
        val inside = model.sample(Vec2(1.0, 0.0)).electric.length
        val outside = model.sample(Vec2(2.5, 0.0)).electric.length
        assertTrue(inside > 0.0)
        assertEquals(0.0, outside, 1e-12)
    }

    @Test
    fun gravityRuleDisablesMassAndGlobalGravity() {
        val source = MassSource(1, Vec2.ZERO, 5.0, extent = 10.0)
        val enabled = FieldModel(listOf(source), globalGravity = Vec2(0.0, -2.0))
        val disabled = FieldModel(listOf(source), globalGravity = Vec2(0.0, -2.0), gravityEnabled = false)
        val point = Vec2(2.0, 1.0)
        assertTrue(enabled.sample(point).gravity.length > 0.0)
        assertEquals(0.0, disabled.sample(point).gravity.length, 1e-12)
    }

    @Test
    fun bodyAcceleratesUnderUniformForce() {
        val engine = SimulationEngine(
            field = FieldModel(
                sources = listOf(ForceZone(1, Vec2(-5.0, -5.0), Vec2(5.0, 5.0), Vec2(1.0, 0.0))),
            ),
            bounds = Aabb(-10.0, -10.0, 10.0, 10.0),
            settings = SimSettings(fixedDt = 1.0 / 60.0, subSteps = 1, collisions = false),
            bodies = mutableListOf(Body(1, Vec2.ZERO, Vec2.ZERO, mass = 2.0)),
        )
        repeat(120) { engine.step() }
        assertTrue(engine.bodies[0].position.x > 0.1)
    }

    @Test
    fun bodyBouncesOffWall() {
        val wall = WallSegment(1, Vec2(-3.0, -2.0), Vec2(3.0, -2.0), thickness = 0.2)
        val engine = SimulationEngine(
            field = FieldModel(listOf(wall)),
            bounds = Aabb(-10.0, -10.0, 10.0, 10.0),
            settings = SimSettings(fixedDt = 0.05, subSteps = 1, collisions = true),
            bodies = mutableListOf(
                Body(1, Vec2(0.0, -1.8), Vec2(0.0, -2.0), mass = 1.0, size = 0.4),
            ),
        )
        engine.step()
        assertTrue(engine.bodies[0].velocity.y > 0.0)
        assertTrue(engine.bodies[0].position.y > -2.0)
    }

    @Test
    fun chargedBodiesRepel() {
        val engine = SimulationEngine(
            field = FieldModel(emptyList()),
            bounds = Aabb(-20.0, -20.0, 20.0, 20.0),
            settings = SimSettings(fixedDt = 1.0 / 120.0, subSteps = 1, collisions = false),
            bodies = mutableListOf(
                Body(1, Vec2(-0.5, 0.0), Vec2.ZERO, charge = 1.0),
                Body(2, Vec2(0.5, 0.0), Vec2.ZERO, charge = 1.0),
            ),
            rules = WorldRules(chargeForceEnabled = true),
        )
        val initial = (engine.bodies[0].position - engine.bodies[1].position).length
        repeat(120) { engine.step() }
        val distance = (engine.bodies[0].position - engine.bodies[1].position).length
        assertTrue(distance > initial)
    }

    @Test
    fun chargeLeakDecaysCharge() {
        val engine = SimulationEngine(
            field = FieldModel(emptyList()),
            bounds = Aabb(-10.0, -10.0, 10.0, 10.0),
            settings = SimSettings(fixedDt = 1.0 / 60.0, collisions = false),
            probes = mutableListOf(Probe(1, Vec2.ZERO, Vec2.ZERO, charge = 1.0)),
            rules = WorldRules(chargeLeak = true),
        )
        repeat(60) { engine.step() }
        assertTrue(engine.probes[0].charge < 0.5)
    }

    @Test
    fun despawnOnImpactRemovesProbes() {
        val engine = SimulationEngine(
            field = FieldModel(emptyList()),
            bounds = Aabb(-10.0, -10.0, 10.0, 10.0),
            settings = SimSettings(fixedDt = 0.01, subSteps = 1, collisions = true),
            probes = mutableListOf(
                Probe(1, Vec2(0.0, 0.0), Vec2.ZERO),
                Probe(2, Vec2(0.05, 0.0), Vec2.ZERO),
            ),
            rules = WorldRules(despawnOnImpact = true),
        )
        engine.step()
        assertTrue(engine.probes.isEmpty())
    }

    @Test
    fun chargeTransferAveragesOnContact() {
        val engine = SimulationEngine(
            field = FieldModel(emptyList()),
            bounds = Aabb(-10.0, -10.0, 10.0, 10.0),
            settings = SimSettings(fixedDt = 0.01, subSteps = 1, collisions = true),
            probes = mutableListOf(
                Probe(1, Vec2(0.0, 0.0), Vec2.ZERO, charge = 1.0),
                Probe(2, Vec2(0.05, 0.0), Vec2.ZERO, charge = -1.0),
            ),
            rules = WorldRules(chargeTransfer = true),
        )
        engine.step()
        assertEquals(0.0, engine.probes[0].charge, 1e-9)
        assertEquals(0.0, engine.probes[1].charge, 1e-9)
    }
}
