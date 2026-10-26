package com.excavplayer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/*
 * Motion tokens for ExcavPlayer.
 *
 * Policy (from the motion audit):
 * - Touched 100+ times/day → no real animation (near-instant).
 * - Every UI animation stays under 300 ms.
 * - Enters/exits ease out fast; on-screen movement eases in-out.
 * - Visible bounce is reserved for deliberate moments only.
 */

/** Strong ease-out for UI entrances/exits. cubic-bezier(0.23, 1, 0.32, 1) */
val ExcavEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Strong ease-in-out for on-screen movement. cubic-bezier(0.77, 0, 0.175, 1) */
val ExcavEaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

/** Drawer/edge-slide curve (player enter, search overlay). cubic-bezier(0.32, 0.72, 0, 1) */
val ExcavEaseDrawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** Duration tiers in ms: press-scale feedback / small UI / containers. */
object ExcavMotion {
    const val Press = 100
    const val Ui = 160
    const val Container = 240
}

/** Press feedback spring (snap on press). */
inline fun <reified T> excavSpringCrisp(): FiniteAnimationSpec<T> =
    spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)

/** Release/settle spring (relax after input). */
inline fun <reified T> excavSpringSettle(): FiniteAnimationSpec<T> =
    spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)

/** Tiered tween helper: excavTween(mode = ExcavMotion.Ui). */
inline fun <reified T> excavTween(
    durationMillis: Int = ExcavMotion.Ui,
    easing: androidx.compose.animation.core.Easing = ExcavEaseOut
): FiniteAnimationSpec<T> = tween(durationMillis, easing = easing)
