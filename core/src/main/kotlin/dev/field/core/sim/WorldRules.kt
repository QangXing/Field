package dev.field.core.sim

import kotlinx.serialization.Serializable

/**
 * Per-world simulation rules exposed in the save settings panel.
 *
 * Defaults deliberately preserve the pre-v3 behaviour of the sandbox:
 * charges do not leak or transfer, particles never despawn, and there is no
 * body-to-body Coulomb force.
 */
@Serializable
data class WorldRules(
    /** 万有引力：质量体与全局重力是否生效。 */
    val gravityEnabled: Boolean = true,

    /** 带电体接触时交换电荷（取平均）。 */
    val chargeTransfer: Boolean = false,

    /** 忽略反弹：粒子与物体直接穿过墙和世界边界。 */
    val ignoreBounce: Boolean = false,

    /** 漏电：电荷随时间指数衰减。 */
    val chargeLeak: Boolean = false,

    /** 显示运动轨迹预测线。 */
    val trajectoryPrediction: Boolean = false,

    /** 显示粒子受力与力矩。 */
    val forceTorqueDisplay: Boolean = false,

    /** 带电体之间产生库仑力。 */
    val chargeForceEnabled: Boolean = false,

    /** 显示物体受力箭头。 */
    val chargeForceDisplay: Boolean = true,

    /** 显示电流方向（运动电荷的方向箭头）。 */
    val currentDirection: Boolean = false,

    /** 粒子撞到墙或物体后消散。 */
    val despawnOnImpact: Boolean = false,

    /** 粒子拥有存活寿命。 */
    val particleLifetime: Boolean = false,

    /** 显示粒子运动轨迹线。 */
    val trails: Boolean = true,

    /** 显示场的箭头。 */
    val fieldArrows: Boolean = true,

    /** 显示物体重心。 */
    val centerOfGravity: Boolean = true,
) {
    companion object {
        const val LEAK_RATE: Double = 0.8
        const val PROBE_LIFETIME: Double = 25.0
    }
}
