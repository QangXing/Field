package dev.field.core.scene

import dev.field.core.fields.FieldModel
import dev.field.core.sim.Body
import dev.field.core.sim.Probe
import dev.field.core.sim.SimulationEngine

/** Builds a runnable engine from a validated scene. */
fun toEngine(scene: Scene): SimulationEngine {
    val field = FieldModel(
        sources = scene.sources,
        globalGravity = scene.world.gravity,
        gravityEnabled = scene.rules.gravityEnabled,
    )
    val probes = scene.probes.mapTo(ArrayList(scene.probes.size)) { def ->
        Probe(
            id = def.id,
            position = def.position,
            velocity = def.velocity,
            mass = def.mass,
            charge = def.charge,
            radius = def.radius,
        )
    }
    val bodies = scene.bodies.mapTo(ArrayList(scene.bodies.size)) { def ->
        Body(
            id = def.id,
            position = def.position,
            velocity = def.velocity,
            mass = def.mass,
            charge = def.charge,
            shape = def.shape,
            size = def.size,
            angle = def.angle,
        )
    }
    return SimulationEngine(
        field = field,
        bounds = scene.world.bounds,
        boundary = scene.world.boundary,
        settings = scene.settings,
        probes = probes,
        bodies = bodies,
        rules = scene.rules,
    )
}
