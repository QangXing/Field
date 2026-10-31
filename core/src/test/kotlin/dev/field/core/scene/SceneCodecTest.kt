package dev.field.core.scene

import dev.field.core.fields.DragRegion
import dev.field.core.fields.ForceZone
import dev.field.core.fields.GravityZone
import dev.field.core.fields.Magnet
import dev.field.core.fields.MassSource
import dev.field.core.fields.PointCharge
import dev.field.core.fields.WallSegment
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import dev.field.core.sim.Boundary
import dev.field.core.sim.Integrator
import dev.field.core.sim.ProbeDef
import dev.field.core.sim.SimSettings
import dev.field.core.viz.HeatmapMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneCodecTest {

    private val codec = SceneCodec()

    private fun validScene(
        sources: List<dev.field.core.fields.FieldSource> = listOf(PointCharge(1, Vec2.ZERO, 1.0)),
        probes: List<ProbeDef> = listOf(ProbeDef(1, Vec2.ZERO)),
        version: Int = 2,
    ): Scene = Scene(
        version = version,
        name = "Test World",
        world = WorldDef(
            bounds = Aabb(-20.0, -12.0, 20.0, 12.0),
            boundary = Boundary.REFLECT,
            gravity = Vec2(0.0, -3.0),
        ),
        settings = SimSettings(
            fixedDt = 1.0 / 60.0,
            subSteps = 2,
            integrator = Integrator.SEMI_IMPLICIT,
            speed = 1.5,
            collisions = true,
            gridSnap = 0.5,
        ),
        sources = sources,
        probes = probes,
        camera = CameraDef(Vec2(0.0, 0.0), 24.0),
        visuals = VisualDef(
            arrows = true,
            streamlines = true,
            heatmap = false,
            trails = true,
            heatmapMode = HeatmapMode.POTENTIAL,
        ),
    )

    private fun fullScene(): Scene = Scene(
        version = 2,
        name = "Full",
        world = WorldDef(
            bounds = Aabb(-20.0, -12.0, 20.0, 12.0),
            boundary = Boundary.WRAP,
            gravity = Vec2(0.0, -3.0),
        ),
        settings = SimSettings(
            fixedDt = 0.02,
            subSteps = 3,
            integrator = Integrator.RK4,
            speed = 0.5,
            collisions = false,
            gridSnap = 1.0,
        ),
        sources = listOf(
            PointCharge(1, Vec2(-3.0, 0.0), 2.0),
            Magnet(2, Vec2(0.0, 4.0), 1.0, 0.785398163),
            MassSource(3, Vec2(0.0, -5.0), 8.0),
            GravityZone(4, Vec2(-4.0, -2.0), Vec2(4.0, 6.0), Vec2(0.0, -3.0)),
            ForceZone(5, Vec2(5.0, -6.0), Vec2(9.0, -2.0), Vec2(1.0, 0.0)),
            DragRegion(6, Vec2(8.0, -12.0), Vec2(12.0, 0.0), 0.6, 0.1, Vec2(0.8, 0.0)),
            WallSegment(7, Vec2(-10.0, -8.0), Vec2(10.0, -8.0), 0.2),
        ),
        probes = listOf(
            ProbeDef(1, Vec2(0.0, 2.0), Vec2(1.0, 0.0), 1.0, 1.0, 0.12),
            ProbeDef(2, Vec2(-1.0, 0.0), Vec2(0.0, 1.0), 2.0, -1.0, 0.2),
        ),
        camera = CameraDef(Vec2(1.0, -1.0), 12.0),
        visuals = VisualDef(true, false, true, false, HeatmapMode.MAGNITUDE),
    )

    @Test
    fun encodeDecodeRoundTripPreservesScene() {
        val scene = fullScene()
        val encoded = codec.encode(scene)
        val result = codec.decode(encoded)

        assertTrue(result is SceneParseResult.Ok)
        val decoded = (result as SceneParseResult.Ok).scene
        assertEquals(scene, decoded)
        assertEquals(encoded, codec.encode(decoded))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val encoded = codec.encode(validScene())
        val withExtra = encoded.replaceFirst("\"name\"", "\"unknownKey\": 123, \"name\"")
        val result = codec.decode(withExtra)

        assertTrue(result is SceneParseResult.Ok)
        assertEquals(validScene(), (result as SceneParseResult.Ok).scene)
    }

    @Test
    fun malformedJsonReturnsErr() {
        val result = codec.decode("{ this is not json")
        assertTrue(result is SceneParseResult.Err)
        assertTrue((result as SceneParseResult.Err).errors.isNotEmpty())
    }

    @Test
    fun validatorRejectsWrongVersion() {
        val result = codec.decode(codec.encode(validScene(version = 1)))
        assertTrue(result is SceneParseResult.Err)
        val errors = (result as SceneParseResult.Err).errors
        assertTrue(errors.any { it.path == "version" })
    }

    @Test
    fun validatorReportsFieldLevelZoneError() {
        val scene = validScene(
            sources = listOf(GravityZone(1, Vec2(0.0, 0.0), Vec2(-1.0, 2.0), Vec2(0.0, -3.0))),
        )
        val errors = SceneValidator.validate(scene)
        val zoneError = errors.firstOrNull { it.path == "sources[0].max" }
        assertNotNull(zoneError)
        assertTrue(zoneError!!.message.contains("必须大于 min"))
    }

    @Test
    fun validatorRejectsOutOfRangeZoneSide() {
        val scene = validScene(
            sources = listOf(DragRegion(1, Vec2(0.0, 0.0), Vec2(0.5, 4.0))),
        )
        val errors = SceneValidator.validate(scene)
        assertTrue(errors.any { it.path == "sources[0]" && it.message.contains("1..20") })
    }

    @Test
    fun validatorRejectsZeroChargeAndDuplicateIds() {
        val zeroCharge = validScene(
            sources = listOf(PointCharge(1, Vec2.ZERO, 0.0)),
        )
        assertTrue(
            SceneValidator.validate(zeroCharge)
                .any { it.path == "sources[0].charge" },
        )

        val duplicate = validScene(
            sources = listOf(
                PointCharge(1, Vec2.ZERO, 1.0),
                PointCharge(1, Vec2(2.0, 0.0), 1.0),
            ),
        )
        assertTrue(
            SceneValidator.validate(duplicate)
                .any { it.path == "sources[1].id" },
        )
    }

    @Test
    fun validatorRejectsInvalidBoundsAndNonFiniteNumbers() {
        val badBounds = validScene().copy(
            world = WorldDef(Aabb(5.0, 0.0, -5.0, 10.0)),
        )
        assertTrue(
            SceneValidator.validate(badBounds)
                .any { it.path == "bounds.maxX" },
        )

        val nonFinite = validScene(
            sources = listOf(PointCharge(1, Vec2(Double.NaN, 0.0), 1.0)),
        )
        assertTrue(
            SceneValidator.validate(nonFinite)
                .any { it.path == "sources[0].position" },
        )
    }

    @Test
    fun toEngineBuildsMatchingEngine() {
        val scene = validScene(
            sources = listOf(
                PointCharge(1, Vec2.ZERO, 1.0),
                WallSegment(2, Vec2(-5.0, -5.0), Vec2(5.0, -5.0)),
            ),
            probes = listOf(
                ProbeDef(1, Vec2(1.0, 2.0), Vec2(3.0, 0.0)),
                ProbeDef(2, Vec2(0.0, 0.0)),
            ),
        )
        val engine = toEngine(scene)

        assertEquals(2, engine.probes.size)
        assertEquals(scene.world.bounds, engine.bounds)
        assertEquals(scene.world.boundary, engine.boundary)
        assertEquals(scene.world.gravity, engine.field.globalGravity)
        assertEquals(Vec2(3.0, 0.0), engine.probes[0].velocity)
    }
}
