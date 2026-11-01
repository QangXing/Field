package dev.field.app.data

import dev.field.core.fields.DragRegion
import dev.field.core.fields.Magnet
import dev.field.core.fields.MassSource
import dev.field.core.fields.PointCharge
import dev.field.core.fields.WallSegment
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import dev.field.core.scene.CameraDef
import dev.field.core.scene.DisplaySettings
import dev.field.core.scene.Scene
import dev.field.core.scene.VisualDef
import dev.field.core.scene.WorldDef
import dev.field.core.sim.Boundary
import dev.field.core.sim.BodyDef
import dev.field.core.sim.BodyShape
import dev.field.core.sim.SimSettings
import dev.field.core.sim.WorldRules

object Defaults {

    val worldBounds: Aabb = Aabb(-20.0, -12.0, 20.0, 12.0)

    fun demoScene(): Scene = Scene(
        name = "演示：匀强电场 + 匀强磁场",
        world = WorldDef(bounds = worldBounds, boundary = Boundary.REFLECT, gravity = Vec2.ZERO),
        settings = SimSettings(),
        sources = listOf(
            // Two uniform electric patches pushing towards the centre.
            PointCharge(1, Vec2(-6.0, 0.0), 2.0, extent = 5.0, angle = 0.0),
            PointCharge(2, Vec2(6.0, 0.0), 2.0, extent = 5.0, angle = Math.PI),
            // Uniform Bz pointing out of the screen.
            Magnet(3, Vec2(0.0, 6.5), 1.5, angle = 0.0, extent = 5.0),
            MassSource(4, Vec2(0.0, -7.0), 8.0),
            DragRegion(
                id = 5,
                min = Vec2(10.0, -5.0),
                max = Vec2(16.0, -1.0),
                linear = 0.6,
                quadratic = 0.1,
            ),
            WallSegment(6, Vec2(-15.0, -10.0), Vec2(-4.0, -10.0), thickness = 0.2),
        ),
        probes = emptyList(),
        camera = CameraDef(center = Vec2.ZERO, zoom = 34.0),
        visuals = VisualDef(),
        bodies = listOf(
            BodyDef(
                id = 1,
                position = Vec2(-2.0, 4.0),
                velocity = Vec2(0.0, -0.6),
                mass = 1.2,
                charge = 1.0,
                shape = BodyShape.CIRCLE,
                size = 0.5,
            ),
            BodyDef(
                id = 2,
                position = Vec2(2.0, -4.0),
                velocity = Vec2(0.0, 0.8),
                mass = 2.0,
                charge = -1.0,
                shape = BodyShape.BOX,
                size = 0.6,
            ),
        ),
        rules = WorldRules(),
        display = DisplaySettings(),
    )

    fun emptyScene(): Scene = Scene(
        name = "新世界",
        world = WorldDef(bounds = worldBounds, boundary = Boundary.REFLECT, gravity = Vec2.ZERO),
        settings = SimSettings(),
        sources = emptyList(),
        probes = emptyList(),
        camera = CameraDef(center = Vec2.ZERO, zoom = 34.0),
        visuals = VisualDef(),
        bodies = emptyList(),
        rules = WorldRules(),
        display = DisplaySettings(),
    )
}
