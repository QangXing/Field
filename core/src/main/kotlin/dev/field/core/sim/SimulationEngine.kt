package dev.field.core.sim

import dev.field.core.fields.FieldModel
import dev.field.core.fields.FieldSample
import dev.field.core.fields.WallSegment
import dev.field.core.math.Aabb
import dev.field.core.math.Vec2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Deterministic fixed-step field simulation for probes and moving bodies.
 *
 * All iteration follows list order; no randomness or parallel reductions are used.
 * Instances are not thread-safe.
 */
class SimulationEngine(
    val field: FieldModel,
    var bounds: Aabb,
    var boundary: Boundary = Boundary.REFLECT,
    var settings: SimSettings = SimSettings(),
    val probes: MutableList<Probe> = mutableListOf(),
    val bodies: MutableList<Body> = mutableListOf(),
    var rules: WorldRules = WorldRules(),
) {
    var time: Double = 0.0

    private var nextProbeId: Long = (probes.maxOfOrNull { it.id } ?: -1L) + 1L
    private var nextBodyId: Long = (bodies.maxOfOrNull { it.id } ?: -1L) + 1L
    private val sampleScratch = FieldSample()

    data class StepStats(
        val physicsNanos: Long,
        val contactCount: Int,
        val liveProbes: Int,
    )

    fun step(dt: Double = settings.fixedDt): StepStats {
        val started = System.nanoTime()
        if (!dt.isFinite() || dt <= 0.0) {
            return StepStats(System.nanoTime() - started, 0, probes.size)
        }

        val steps = settings.subSteps.coerceAtLeast(1)
        val h = dt / steps
        var contacts = 0

        for (probe in probes) probe.prevPosition = probe.position
        for (body in bodies) body.prevPosition = body.position

        repeat(steps) {
            for (probe in probes) {
                probe.impact = false
                if (probe.faulted) continue
                integrate(probe, h)
            }
            for (body in bodies) {
                if (body.faulted) continue
                integrateBody(body, h)
            }
            if (rules.chargeForceEnabled) applyChargeCoupling(h)
            applyBoundary()
            applyBodyBoundary()
            if (settings.collisions) {
                contacts += resolveCollisions()
                contacts += resolveWallCollisions()
                contacts += resolveBodyCollisions()
                contacts += resolveBodyWallCollisions()
                contacts += resolveProbeBodyCollisions()
            }
            if (rules.chargeLeak) leakCharges(h)
            if (rules.despawnOnImpact) removeImpactedProbes()
            if (rules.particleLifetime) expireProbes()
        }

        time += dt
        for (probe in probes) {
            probe.age += dt
            probe.pushTrail(TRAIL_MAX_POINTS)
        }
        for (body in bodies) {
            body.age += dt
        }

        return StepStats(System.nanoTime() - started, contacts, probes.size)
    }

    // ---------------------------------------------------------------- probes

    fun addProbe(p: Probe) {
        probes.add(p)
        if (p.id >= nextProbeId) {
            nextProbeId = p.id + 1L
        }
    }

    fun launch(
        count: Int,
        origin: Vec2,
        direction: Vec2,
        speed: Double,
        charge: Double,
        spreadRadians: Double,
        rng: Random,
    ) {
        if (count <= 0) return
        val baseAngle = if (direction.lengthSquared > 0.0) atan2(direction.y, direction.x) else 0.0
        val halfSpread = if (spreadRadians > 0.0) spreadRadians * 0.5 else 0.0

        repeat(count) {
            val angle = if (halfSpread > 0.0) {
                baseAngle + rng.nextDouble(-halfSpread, halfSpread)
            } else {
                baseAngle
            }
            val velocity = Vec2(cos(angle) * speed, sin(angle) * speed)
            probes.add(
                Probe(
                    id = nextProbeId++,
                    position = origin,
                    velocity = velocity,
                    charge = charge,
                ),
            )
        }
    }

    fun clearProbes() {
        probes.clear()
        nextProbeId = 0L
    }

    // ---------------------------------------------------------------- bodies

    fun addBody(body: Body) {
        bodies.add(body)
        if (body.id >= nextBodyId) {
            nextBodyId = body.id + 1L
        }
    }

    fun nextBodyId(): Long = nextBodyId++

    fun launchBody(
        shape: BodyShape,
        origin: Vec2,
        direction: Vec2,
        speed: Double,
        mass: Double,
        charge: Double,
        size: Double,
        spreadRadians: Double,
        rng: Random,
    ) {
        val baseAngle = if (direction.lengthSquared > 0.0) atan2(direction.y, direction.x) else 0.0
        val halfSpread = if (spreadRadians > 0.0) spreadRadians * 0.5 else 0.0
        val angle = if (halfSpread > 0.0) {
            baseAngle + rng.nextDouble(-halfSpread, halfSpread)
        } else {
            baseAngle
        }
        val velocity = Vec2(cos(angle) * speed, sin(angle) * speed)
        addBody(
            Body(
                id = nextBodyId(),
                position = origin,
                velocity = velocity,
                mass = mass,
                charge = charge,
                shape = shape,
                size = size,
            ),
        )
    }

    fun clearBodies() {
        bodies.clear()
        nextBodyId = 0L
    }

    /** Acceleration of [probe] if it were at [position] with [velocity]. */
    internal fun accelerationAt(probe: Probe, position: Vec2, velocity: Vec2): Vec2 {
        field.sampleInto(position, sampleScratch)
        val invMass = inverseMass(probe)
        val qOverM = probe.charge * invMass

        val e = sampleScratch.electric
        val bz = sampleScratch.magneticBz
        var ax = qOverM * e.x + qOverM * bz * -velocity.y +
            sampleScratch.gravity.x + sampleScratch.uniform.x
        var ay = qOverM * e.y + qOverM * bz * velocity.x +
            sampleScratch.gravity.y + sampleScratch.uniform.y

        val rx = velocity.x - sampleScratch.wind.x
        val ry = velocity.y - sampleScratch.wind.y
        val relSpeed = sqrt(rx * rx + ry * ry)
        val drag = probe.dragScale * invMass
        val c1 = sampleScratch.dragLinear
        val c2 = sampleScratch.dragQuadratic
        ax -= drag * (c1 * rx + c2 * relSpeed * rx)
        ay -= drag * (c1 * ry + c2 * relSpeed * ry)

        return Vec2(ax, ay)
    }

    private fun accelerationAtBody(body: Body, position: Vec2, velocity: Vec2): Vec2 {
        field.sampleInto(position, sampleScratch)
        val invMass = inverseMass(body.mass)
        val qOverM = body.charge * invMass

        val e = sampleScratch.electric
        val bz = sampleScratch.magneticBz
        var ax = qOverM * e.x + qOverM * bz * -velocity.y +
            sampleScratch.gravity.x + sampleScratch.uniform.x
        var ay = qOverM * e.y + qOverM * bz * velocity.x +
            sampleScratch.gravity.y + sampleScratch.uniform.y

        val rx = velocity.x - sampleScratch.wind.x
        val ry = velocity.y - sampleScratch.wind.y
        val relSpeed = sqrt(rx * rx + ry * ry)
        val drag = invMass
        val c1 = sampleScratch.dragLinear
        val c2 = sampleScratch.dragQuadratic
        ax -= drag * (c1 * rx + c2 * relSpeed * rx)
        ay -= drag * (c1 * ry + c2 * relSpeed * ry)

        return Vec2(ax, ay)
    }

    private fun integrate(probe: Probe, h: Double) {
        val oldPosition = probe.position
        when (settings.integrator) {
            Integrator.SEMI_IMPLICIT -> integrateSemiImplicit(probe, h)
            Integrator.RK4 -> integrateRk4(probe, h)
        }

        val velocity = probe.velocity
        if (!velocity.isFinite() || !probe.position.isFinite()) {
            probe.position = if (oldPosition.isFinite()) oldPosition else bounds.center
            probe.velocity = Vec2.ZERO
            probe.faulted = true
            return
        }

        clampSpeed(probe)
    }

    private fun integrateBody(body: Body, h: Double) {
        val oldPosition = body.position
        when (settings.integrator) {
            Integrator.SEMI_IMPLICIT -> integrateBodySemiImplicit(body, h)
            Integrator.RK4 -> integrateBodyRk4(body, h)
        }

        if (!body.velocity.isFinite() || !body.position.isFinite()) {
            body.position = if (oldPosition.isFinite()) oldPosition else bounds.center
            body.velocity = Vec2.ZERO
            body.faulted = true
            return
        }
        val speedSquared = body.velocity.lengthSquared
        if (speedSquared > BODY_MAX_SPEED * BODY_MAX_SPEED) {
            val scale = BODY_MAX_SPEED / sqrt(speedSquared)
            body.velocity = body.velocity * scale
        }
    }

    private fun clampSpeed(probe: Probe) {
        val speedSquared = probe.velocity.lengthSquared
        if (speedSquared > MAX_SPEED * MAX_SPEED) {
            val scale = MAX_SPEED / sqrt(speedSquared)
            probe.velocity = probe.velocity * scale
        }
    }

    private fun integrateSemiImplicit(probe: Probe, h: Double) {
        val a = accelerationAt(probe, probe.position, probe.velocity)
        val newVelocity = probe.velocity + a * h
        probe.velocity = newVelocity
        probe.position = probe.position + newVelocity * h
    }

    private fun integrateBodySemiImplicit(body: Body, h: Double) {
        val a = accelerationAtBody(body, body.position, body.velocity)
        body.force = a * body.mass
        val newVelocity = body.velocity + a * h
        body.velocity = newVelocity
        body.position = body.position + newVelocity * h
    }

    private fun integrateRk4(probe: Probe, h: Double) {
        val p0 = probe.position
        val v0 = probe.velocity

        val a1 = accelerationAt(probe, p0, v0)

        val p2 = p0 + v0 * (h * 0.5)
        val v2 = v0 + a1 * (h * 0.5)
        val a2 = accelerationAt(probe, p2, v2)

        val p3 = p0 + v2 * (h * 0.5)
        val v3 = v0 + a2 * (h * 0.5)
        val a3 = accelerationAt(probe, p3, v3)

        val p4 = p0 + v3 * h
        val v4 = v0 + a3 * h
        val a4 = accelerationAt(probe, p4, v4)

        probe.position = p0 + (v0 + v2 * 2.0 + v3 * 2.0 + v4) * (h / 6.0)
        probe.velocity = v0 + (a1 + a2 * 2.0 + a3 * 2.0 + a4) * (h / 6.0)
    }

    private fun integrateBodyRk4(body: Body, h: Double) {
        val p0 = body.position
        val v0 = body.velocity

        val a1 = accelerationAtBody(body, p0, v0)

        val p2 = p0 + v0 * (h * 0.5)
        val v2 = v0 + a1 * (h * 0.5)
        val a2 = accelerationAtBody(body, p2, v2)

        val p3 = p0 + v2 * (h * 0.5)
        val v3 = v0 + a2 * (h * 0.5)
        val a3 = accelerationAtBody(body, p3, v3)

        val p4 = p0 + v3 * h
        val v4 = v0 + a3 * h
        val a4 = accelerationAtBody(body, p4, v4)

        body.position = p0 + (v0 + v2 * 2.0 + v3 * 2.0 + v4) * (h / 6.0)
        body.velocity = v0 + (a1 + a2 * 2.0 + a3 * 2.0 + a4) * (h / 6.0)
        body.force = ((a1 + a2 * 2.0 + a3 * 2.0 + a4) / 6.0) * body.mass
    }

    // ---------------------------------------------------------------- coupling

    private fun applyChargeCoupling(h: Double) {
        val list = bodies
        val n = list.size
        if (n < 2) return
        for (i in 0 until n) {
            val a = list[i]
            if (a.faulted || a.charge == 0.0) continue
            for (j in i + 1 until n) {
                val b = list[j]
                if (b.faulted || b.charge == 0.0) continue
                val dx = a.position.x - b.position.x
                val dy = a.position.y - b.position.y
                val r2 = dx * dx + dy * dy + COULOMB_SOFTENING * COULOMB_SOFTENING
                if (r2 > COULOMB_CUTOFF * COULOMB_CUTOFF) continue
                val r = sqrt(r2)
                val f = a.charge * b.charge / r2
                val fx = f * dx / r
                val fy = f * dy / r

                val invA = inverseMass(a.mass)
                val invB = inverseMass(b.mass)
                a.velocity = Vec2(a.velocity.x + fx * invA * h, a.velocity.y + fy * invA * h)
                b.velocity = Vec2(b.velocity.x - fx * invB * h, b.velocity.y - fy * invB * h)
                a.force = Vec2(a.force.x + fx, a.force.y + fy)
                b.force = Vec2(b.force.x - fx, b.force.y - fy)
            }
        }
    }

    private fun leakCharges(h: Double) {
        val factor = exp(-WorldRules.LEAK_RATE * h)
        for (probe in probes) {
            probe.charge *= factor
        }
        for (body in bodies) {
            body.charge *= factor
        }
    }

    private fun expireProbes() {
        val iterator = probes.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().age > WorldRules.PROBE_LIFETIME) {
                iterator.remove()
            }
        }
    }

    private fun removeImpactedProbes() {
        probes.removeAll { it.impact }
    }

    // ---------------------------------------------------------------- boundaries

    private fun applyBoundary() {
        when (boundary) {
            Boundary.REFLECT -> {
                if (rules.ignoreBounce) return
                for (probe in probes) reflectProbe(probe)
            }

            Boundary.WRAP -> {
                for (probe in probes) wrapProbe(probe)
            }

            Boundary.OPEN -> {
                val expanded = bounds.expand(OPEN_MARGIN)
                val iterator = probes.iterator()
                while (iterator.hasNext()) {
                    if (!expanded.contains(iterator.next().position)) {
                        iterator.remove()
                    }
                }
            }
        }
    }

    private fun applyBodyBoundary() {
        when (boundary) {
            Boundary.REFLECT -> {
                if (rules.ignoreBounce) return
                for (body in bodies) reflectBody(body)
            }

            Boundary.WRAP -> {
                for (body in bodies) wrapBody(body)
            }

            Boundary.OPEN -> {
                val expanded = bounds.expand(OPEN_MARGIN)
                bodies.removeAll { !expanded.contains(it.position) }
            }
        }
    }

    private fun reflectProbe(probe: Probe) {
        val p = probe.position
        val v = probe.velocity
        var x = p.x
        var y = p.y
        var vx = v.x
        var vy = v.y
        var changed = false

        if (x < bounds.minX) {
            x = bounds.minX
            if (vx < 0.0) vx = -vx
            changed = true
        } else if (x > bounds.maxX) {
            x = bounds.maxX
            if (vx > 0.0) vx = -vx
            changed = true
        }
        if (y < bounds.minY) {
            y = bounds.minY
            if (vy < 0.0) vy = -vy
            changed = true
        } else if (y > bounds.maxY) {
            y = bounds.maxY
            if (vy > 0.0) vy = -vy
            changed = true
        }

        if (changed) {
            probe.position = Vec2(x, y)
            probe.velocity = Vec2(vx, vy)
        }
    }

    private fun reflectBody(body: Body) {
        val r = body.collisionRadius.coerceAtLeast(0.05)
        val p = body.position
        val v = body.velocity
        var x = p.x
        var y = p.y
        var vx = v.x
        var vy = v.y

        if (x - r < bounds.minX) {
            x = bounds.minX + r
            if (vx < 0.0) vx = -vx
        } else if (x + r > bounds.maxX) {
            x = bounds.maxX - r
            if (vx > 0.0) vx = -vx
        }
        if (y - r < bounds.minY) {
            y = bounds.minY + r
            if (vy < 0.0) vy = -vy
        } else if (y + r > bounds.maxY) {
            y = bounds.maxY - r
            if (vy > 0.0) vy = -vy
        }

        if (x != p.x || y != p.y) {
            body.position = Vec2(x, y)
            body.velocity = Vec2(vx, vy)
        }
    }

    private fun wrapProbe(probe: Probe) {
        val p = probe.position
        val width = bounds.width
        val height = bounds.height
        var x = p.x
        var y = p.y

        if (width > 0.0 && (x < bounds.minX || x > bounds.maxX)) {
            x = bounds.minX + (((x - bounds.minX) % width) + width) % width
        }
        if (height > 0.0 && (y < bounds.minY || y > bounds.maxY)) {
            y = bounds.minY + (((y - bounds.minY) % height) + height) % height
        }

        if (x != p.x || y != p.y) {
            probe.position = Vec2(x, y)
        }
    }

    private fun wrapBody(body: Body) {
        val p = body.position
        val width = bounds.width
        val height = bounds.height
        var x = p.x
        var y = p.y

        if (width > 0.0 && (x < bounds.minX || x > bounds.maxX)) {
            x = bounds.minX + (((x - bounds.minX) % width) + width) % width
        }
        if (height > 0.0 && (y < bounds.minY || y > bounds.maxY)) {
            y = bounds.minY + (((y - bounds.minY) % height) + height) % height
        }
        if (x != p.x || y != p.y) {
            body.position = Vec2(x, y)
        }
    }

    // ---------------------------------------------------------------- collisions

    private fun resolveCollisions(): Int {
        val n = probes.size
        if (n < 2) return 0

        var contacts = 0
        if (n > SPATIAL_HASH_THRESHOLD) {
            val maxRadius = probes.maxOf { it.radius }
            val cellSize = (2.0 * maxRadius).coerceAtLeast(MIN_CELL_SIZE)
            val grid = HashMap<Long, MutableList<Int>>()
            for (i in 0 until n) {
                val key = cellKey(probes[i].position, cellSize)
                grid.getOrPut(key) { ArrayList() }.add(i)
            }
            for (i in 0 until n) {
                val probe = probes[i]
                val cx = cellIndex(probe.position.x, cellSize)
                val cy = cellIndex(probe.position.y, cellSize)
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val bucket = grid[cellKey(cx + dx, cy + dy)] ?: continue
                        for (j in bucket) {
                            if (j < i && collidePair(probes[j], probe)) {
                                contacts++
                            }
                        }
                    }
                }
            }
        } else {
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    if (collidePair(probes[i], probes[j])) {
                        contacts++
                    }
                }
            }
        }
        return contacts
    }

    private fun resolveWallCollisions(): Int {
        if (rules.ignoreBounce) return 0
        var contacts = 0
        for (probe in probes) {
            for (source in field.sources) {
                if (source is WallSegment && collideWall(probe, source)) {
                    contacts++
                }
            }
        }
        return contacts
    }

    private fun resolveBodyCollisions(): Int {
        var contacts = 0
        val n = bodies.size
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (collideBodies(bodies[i], bodies[j])) contacts++
            }
        }
        return contacts
    }

    private fun resolveBodyWallCollisions(): Int {
        if (rules.ignoreBounce) return 0
        var contacts = 0
        for (body in bodies) {
            for (source in field.sources) {
                if (source is WallSegment && collideBodyWall(body, source)) contacts++
            }
        }
        return contacts
    }

    private fun resolveProbeBodyCollisions(): Int {
        var contacts = 0
        for (probe in probes) {
            for (body in bodies) {
                if (collideProbeBody(probe, body)) contacts++
            }
        }
        return contacts
    }

    private fun collidePair(a: Probe, b: Probe): Boolean {
        val dx = b.position.x - a.position.x
        val dy = b.position.y - a.position.y
        if (!dx.isFinite() || !dy.isFinite()) return false
        val minDistance = a.radius + b.radius
        if (minDistance <= 0.0) return false
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared >= minDistance * minDistance) return false

        val distance = sqrt(distanceSquared)
        val nx: Double
        val ny: Double
        if (distance > 1e-9) {
            nx = dx / distance
            ny = dy / distance
        } else {
            nx = 1.0
            ny = 0.0
        }

        val invA = inverseMass(a)
        val invB = inverseMass(b)
        val invSum = invA + invB
        if (invSum <= 0.0) return false

        val overlap = minDistance - distance
        val pushA = overlap * (invA / invSum)
        val pushB = overlap * (invB / invSum)
        a.position = Vec2(a.position.x - nx * pushA, a.position.y - ny * pushA)
        b.position = Vec2(b.position.x + nx * pushB, b.position.y + ny * pushB)

        val relativeNormal = (b.velocity.x - a.velocity.x) * nx +
            (b.velocity.y - a.velocity.y) * ny
        if (relativeNormal < 0.0) {
            val impulse = -(1.0 + RESTITUTION) * relativeNormal / invSum
            a.velocity = Vec2(
                a.velocity.x - nx * impulse * invA,
                a.velocity.y - ny * impulse * invA,
            )
            b.velocity = Vec2(
                b.velocity.x + nx * impulse * invB,
                b.velocity.y + ny * impulse * invB,
            )
        }
        a.impact = true
        b.impact = true
        if (rules.chargeTransfer) {
            val shared = (a.charge + b.charge) * 0.5
            a.charge = shared
            b.charge = shared
        }
        return true
    }

    private fun collideBodies(a: Body, b: Body): Boolean {
        val dx = b.position.x - a.position.x
        val dy = b.position.y - a.position.y
        val minDistance = a.collisionRadius + b.collisionRadius
        if (minDistance <= 0.0) return false
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared >= minDistance * minDistance) return false

        val distance = sqrt(distanceSquared)
        val nx = if (distance > 1e-9) dx / distance else 1.0
        val ny = if (distance > 1e-9) dy / distance else 0.0

        val invA = inverseMass(a.mass)
        val invB = inverseMass(b.mass)
        val invSum = invA + invB
        if (invSum <= 0.0) return false

        val overlap = minDistance - distance
        a.position = Vec2(a.position.x - nx * overlap * (invA / invSum), a.position.y - ny * overlap * (invA / invSum))
        b.position = Vec2(b.position.x + nx * overlap * (invB / invSum), b.position.y + ny * overlap * (invB / invSum))

        val relativeNormal = (b.velocity.x - a.velocity.x) * nx + (b.velocity.y - a.velocity.y) * ny
        if (relativeNormal < 0.0) {
            val impulse = -(1.0 + RESTITUTION) * relativeNormal / invSum
            a.velocity = Vec2(a.velocity.x - nx * impulse * invA, a.velocity.y - ny * impulse * invA)
            b.velocity = Vec2(b.velocity.x + nx * impulse * invB, b.velocity.y + ny * impulse * invB)
        }
        if (rules.chargeTransfer) {
            val shared = (a.charge + b.charge) * 0.5
            a.charge = shared
            b.charge = shared
        }
        return true
    }

    private fun collideProbeBody(probe: Probe, body: Body): Boolean {
        val dx = probe.position.x - body.position.x
        val dy = probe.position.y - body.position.y
        val minDistance = probe.radius + body.collisionRadius
        if (minDistance <= 0.0) return false
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared >= minDistance * minDistance) return false

        val distance = sqrt(distanceSquared)
        val nx = if (distance > 1e-9) dx / distance else 1.0
        val ny = if (distance > 1e-9) dy / distance else 0.0
        probe.position = Vec2(body.position.x + nx * minDistance, body.position.y + ny * minDistance)

        val normalSpeed = probe.velocity.x * nx + probe.velocity.y * ny
        if (normalSpeed < 0.0) {
            val impulse = -(1.0 + RESTITUTION) * normalSpeed
            probe.velocity = Vec2(probe.velocity.x + nx * impulse, probe.velocity.y + ny * impulse)
        }
        probe.impact = true
        if (rules.chargeTransfer) {
            probe.charge = (probe.charge + body.charge) * 0.5
        }
        return true
    }

    private fun collideWall(probe: Probe, wall: WallSegment): Boolean {
        val sx = wall.start.x
        val sy = wall.start.y
        val wx = wall.end.x - sx
        val wy = wall.end.y - sy
        val lengthSquared = wx * wx + wy * wy
        if (lengthSquared < 1e-12) return false

        val t = (((probe.position.x - sx) * wx + (probe.position.y - sy) * wy) / lengthSquared)
            .coerceIn(0.0, 1.0)
        val closestX = sx + wx * t
        val closestY = sy + wy * t
        var nx = probe.position.x - closestX
        var ny = probe.position.y - closestY
        val distanceSquared = nx * nx + ny * ny
        val minDistance = probe.radius + wall.thickness * 0.5
        if (minDistance <= 0.0 || distanceSquared >= minDistance * minDistance) return false

        val distance = sqrt(distanceSquared)
        if (distance > 1e-9) {
            nx /= distance
            ny /= distance
        } else {
            val length = sqrt(lengthSquared)
            nx = -wy / length
            ny = wx / length
        }

        probe.position = Vec2(closestX + nx * minDistance, closestY + ny * minDistance)

        val normalSpeed = probe.velocity.x * nx + probe.velocity.y * ny
        if (normalSpeed < 0.0) {
            val impulse = -(1.0 + RESTITUTION) * normalSpeed
            probe.velocity = Vec2(
                probe.velocity.x + nx * impulse,
                probe.velocity.y + ny * impulse,
            )
        }
        probe.impact = true
        return true
    }

    private fun collideBodyWall(body: Body, wall: WallSegment): Boolean {
        val sx = wall.start.x
        val sy = wall.start.y
        val wx = wall.end.x - sx
        val wy = wall.end.y - sy
        val lengthSquared = wx * wx + wy * wy
        if (lengthSquared < 1e-12) return false

        val t = (((body.position.x - sx) * wx + (body.position.y - sy) * wy) / lengthSquared)
            .coerceIn(0.0, 1.0)
        val closestX = sx + wx * t
        val closestY = sy + wy * t
        var nx = body.position.x - closestX
        var ny = body.position.y - closestY
        val distanceSquared = nx * nx + ny * ny
        val minDistance = body.collisionRadius + wall.thickness * 0.5
        if (minDistance <= 0.0 || distanceSquared >= minDistance * minDistance) return false

        val distance = sqrt(distanceSquared)
        if (distance > 1e-9) {
            nx /= distance
            ny /= distance
        } else {
            val length = sqrt(lengthSquared)
            nx = -wy / length
            ny = wx / length
        }

        body.position = Vec2(closestX + nx * minDistance, closestY + ny * minDistance)

        val normalSpeed = body.velocity.x * nx + body.velocity.y * ny
        if (normalSpeed < 0.0) {
            val impulse = -(1.0 + RESTITUTION) * normalSpeed
            body.velocity = Vec2(
                body.velocity.x + nx * impulse,
                body.velocity.y + ny * impulse,
            )
        }
        return true
    }

    private fun inverseMass(probe: Probe): Double =
        if (probe.mass > 0.0 && probe.mass.isFinite()) 1.0 / probe.mass else 0.0

    private fun inverseMass(mass: Double): Double =
        if (mass > 0.0 && mass.isFinite()) 1.0 / mass else 0.0

    private fun cellKey(position: Vec2, cellSize: Double): Long =
        cellKey(cellIndex(position.x, cellSize), cellIndex(position.y, cellSize))

    private fun cellKey(cx: Int, cy: Int): Long =
        (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFF_FFFFL)

    private fun cellIndex(value: Double, cellSize: Double): Int =
        floor(value / cellSize).toInt()

    companion object {
        const val MAX_SPEED: Double = 60.0
        const val BODY_MAX_SPEED: Double = 25.0
        const val RESTITUTION: Double = 0.2
        const val OPEN_MARGIN: Double = 0.5
        const val TRAIL_MAX_POINTS: Int = 32
        const val SPATIAL_HASH_THRESHOLD: Int = 200
        const val COULOMB_SOFTENING: Double = 0.25
        const val COULOMB_CUTOFF: Double = 24.0
        private const val MIN_CELL_SIZE: Double = 0.1
    }
}

/** Convenience alias for [SimulationEngine.StepStats]. */
typealias StepStats = SimulationEngine.StepStats
