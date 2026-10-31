package dev.field.app.data

import dev.field.core.fields.DragRegion
import dev.field.core.fields.Magnet
import dev.field.core.fields.MassSource
import dev.field.core.fields.PointCharge
import dev.field.core.fields.WallSegment
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import dev.field.core.scene.CameraDef
import dev.field.core.scene.Scene
import dev.field.core.scene.VisualDef
import dev.field.core.scene.WorldDef
import dev.field.core.sim.Boundary
import dev.field.core.sim.SimSettings

object Defaults {

    val worldBounds: Aabb = Aabb(-20.0, -12.0, 20.0, 12.0)

    fun demoScene(): Scene = Scene(
        name = "演示：电场 + 磁体",
        world = WorldDef(bounds = worldBounds, boundary = Boundary.REFLECT, gravity = Vec2.ZERO),
        settings = SimSettings(),
        sources = listOf(
            PointCharge(1, Vec2(-6.0, 0.0), 1.0),
            PointCharge(2, Vec2(6.0, 0.0), -1.0),
            Magnet(3, Vec2(0.0, 6.5), 1.0, angle = 0.0),
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
        camera = CameraDef(center = Vec2.ZERO, zoom = 26.0),
        visuals = VisualDef(),
    )

    fun emptyScene(): Scene = Scene(
        name = "新世界",
        world = WorldDef(bounds = worldBounds, boundary = Boundary.REFLECT, gravity = Vec2.ZERO),
        settings = SimSettings(),
        sources = emptyList(),
        probes = emptyList(),
        camera = CameraDef(center = Vec2.ZERO, zoom = 26.0),
        visuals = VisualDef(),
    )
}
