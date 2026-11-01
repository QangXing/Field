package dev.field.core.fields

import dev.field.core.math.Vec2

enum class FieldKind { ELECTRIC, MAGNETIC, GRAVITY, DRAG, UNIFORM_FORCE }

/**
 * A superposition of field sources plus a uniform gravity contribution.
 *
 * Sampling is stateful (scratch buffers are reused), so a single instance must not be
 * sampled concurrently from multiple threads.
 */
class FieldModel(
    val sources: List<FieldSource>,
    val globalGravity: Vec2 = Vec2.ZERO,
    val kE: Double = 1.0,
    val kM: Double = 1.0,
    val G: Double = 1.0,
    val softening: Double = 0.05,
    /** 万有引力开关：关闭后不累加质量体引力与全局重力。 */
    val gravityEnabled: Boolean = true,
) {
    private val vectorScratch = FieldSample()

    /** Samples the field at [p]. Allocates exactly one [FieldSample]. */
    fun sample(p: Vec2): FieldSample {
        val out = FieldSample()
        sampleInto(p, out)
        return out
    }

    /** Samples into a caller-owned [FieldSample], allocating no new sample object. */
    fun sampleInto(p: Vec2, out: FieldSample) {
        out.reset()
        for (source in sources) {
            source.softening = softening
            if (source is MassSource) {
                if (!gravityEnabled) continue
                source.gravityConstant = G
            }
            source.accumulate(p, out)
        }

        if (kE != 1.0) {
            out.electric = out.electric * kE
            out.potential *= kE
        }
        if (kM != 1.0) {
            out.magneticBz *= kM
        }
        if (gravityEnabled && (globalGravity.x != 0.0 || globalGravity.y != 0.0)) {
            out.gravity = out.gravity + globalGravity
        }

        val dragTotal = out.dragLinear + out.dragQuadratic
        if (dragTotal > MAX_DRAG) {
            val scale = MAX_DRAG / dragTotal
            out.dragLinear *= scale
            out.dragQuadratic *= scale
        }
    }

    /**
     * Returns the vector field used for arrows/streamlines of the requested kind.
     *
     * MAGNETIC is an out-of-plane uniform field in this build, so it has no in-plane
     * arrow direction and returns [Vec2.ZERO]; the canvas draws ⊙/⊗ symbols instead.
     */
    fun fieldVector(p: Vec2, kind: FieldKind): Vec2 = when (kind) {
        FieldKind.ELECTRIC -> {
            sampleInto(p, vectorScratch)
            vectorScratch.electric
        }

        FieldKind.MAGNETIC -> Vec2.ZERO

        FieldKind.GRAVITY -> {
            sampleInto(p, vectorScratch)
            vectorScratch.gravity
        }

        FieldKind.DRAG -> {
            sampleInto(p, vectorScratch)
            vectorScratch.wind
        }

        FieldKind.UNIFORM_FORCE -> {
            sampleInto(p, vectorScratch)
            vectorScratch.uniform
        }
    }

    fun potential(p: Vec2): Double = sample(p).potential

    companion object {
        const val MAX_DRAG: Double = 5.0
    }
}
