// =====================================================================
// ComboParticleOverlay.kt
// Block Quest — Canvas particle burst for combos and line clears (Semana 4)
//
// Design:
//  • Pure Compose — no external library. Uses withFrameNanos inside a
//    LaunchedEffect to drive each particle's position and alpha.
//  • Particles burst from the centre of the board when a combo or
//    a multi-line clear fires.
//  • The overlay is drawn on top of the board via Box{} stacking.
//  • ScorePopup draws a "+N pts" text that floats upward and fades.
// =====================================================================

package com.blockquest.presentation.ui.screen.gameplay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ── Data ─────────────────────────────────────────────────────────────────

private data class Particle(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val color: Color,
    var alpha: Float = 1f,
    var life: Float  = 1f,   // 1 → 0 over lifetime
)

private fun burst(
    cx: Float,
    cy: Float,
    count: Int,
    colors: List<Color>,
    speed: Float = 18f,
): List<Particle> = List(count) {
    val angle  = Random.nextFloat() * 2 * Math.PI.toFloat()
    val s      = speed * (0.6f + Random.nextFloat() * 0.8f)
    Particle(
        x      = cx,
        y      = cy,
        vx     = cos(angle) * s,
        vy     = sin(angle) * s,
        radius = (4f + Random.nextFloat() * 6f),
        color  = colors[it % colors.size],
    )
}

// ── Public composable ─────────────────────────────────────────────────────

/**
 * Draws a particle burst when [trigger] changes to a non-null value.
 * Place this as an overlay inside the board's [Box].
 *
 * @param trigger        Change this to fire a new burst (combo index, clear count, etc.)
 * @param originFraction [Offset] as a fraction of the composable's size (0..1).
 *                       Defaults to 0.5,0.5 (board centre).
 * @param particleCount  Number of particles per burst.
 * @param durationMs     How long each burst animates.
 */
@Composable
fun ComboParticleOverlay(
    trigger: Any?,
    modifier: Modifier = Modifier,
    originFraction: Offset = Offset(0.5f, 0.5f),
    particleCount: Int = 28,
    durationMs: Long = 700L,
) {
    val primaryColor   = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor  = MaterialTheme.colorScheme.tertiary

    var particles by remember { mutableStateOf(emptyList<Particle>()) }

    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect

        // We need the measured width/height but we don't have them
        // at this point. We use a large default and let the Canvas
        // measure its own bounds via DrawScope.size.
        // The actual spawn point is computed inside the draw loop.
        particles = emptyList()  // reset first so old particles are cleared

        // Kick off a new burst — origin resolved in the draw lambda.
        var needsOrigin = true
        var cx = 0f; var cy = 0f

        var lastFrameNs = withFrameNanos { it }
        val deadlineNs  = lastFrameNs + durationMs * 1_000_000L

        // Temporary list mutated each frame
        val working = mutableListOf<Particle>()

        while (true) {
            val frameNs = withFrameNanos { it }
            val dtMs    = (frameNs - lastFrameNs) / 1_000_000f
            lastFrameNs = frameNs

            if (working.isEmpty() && needsOrigin) {
                // First frame — we still don't have canvas size.
                // Spawn at a placeholder (600×600 centre) and let
                // the real canvas draw correct it next frame.
                working.addAll(
                    burst(
                        cx    = 300f,
                        cy    = 300f,
                        count = particleCount,
                        colors = listOf(primaryColor, secondaryColor, tertiaryColor),
                    )
                )
                needsOrigin = false
            }

            // Physics step
            val gravity = 0.4f
            working.forEach { p ->
                p.x    += p.vx * dtMs
                p.y    += p.vy * dtMs + gravity * dtMs
                p.life -= dtMs / durationMs
                p.alpha = p.life.coerceIn(0f, 1f)
            }
            working.removeAll { it.life <= 0f }

            particles = working.toList()

            if (frameNs >= deadlineNs || working.isEmpty()) break
        }
        particles = emptyList()
    }

    if (particles.isNotEmpty()) {
        Canvas(modifier = modifier.fillMaxSize()) {
            // Re-centre particles to actual canvas centre on first draw.
            val actualCx = size.width  * originFraction.x
            val actualCy = size.height * originFraction.y
            particles.forEach { p ->
                drawCircle(
                    color  = p.color.copy(alpha = p.alpha),
                    radius = p.radius,
                    center = Offset(
                        x = actualCx + (p.x - 300f),
                        y = actualCy + (p.y - 300f),
                    ),
                )
            }
        }
    }
}

// ── Score pop-up ─────────────────────────────────────────────────────────

/**
 * Displays a "+N pts" label that floats upward and fades over [durationMs].
 * Place this as an overlay on the board.
 *
 * @param points  Points to display. Pass null or 0 to hide.
 * @param trigger Change to re-trigger the animation (e.g. pass the score delta).
 */
@Composable
fun ScorePopup(
    points: Int,
    trigger: Any?,
    modifier: Modifier = Modifier,
    durationMs: Int    = 900,
) {
    val offsetY = remember { Animatable(0f) }
    val alpha   = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (points <= 0) return@LaunchedEffect
        offsetY.snapTo(0f)
        alpha.snapTo(1f)
        kotlinx.coroutines.coroutineScope {
            launch { offsetY.animateTo(-80f, tween(durationMs)) }
            launch { alpha.animateTo(0f,     tween(durationMs, easing = { t -> if (t < 0.5f) 1f else 1f - (t - 0.5f) * 2f })) }
        }
    }

    if (alpha.value > 0f) {
        androidx.compose.material3.Text(
            text  = "+$points",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color      = MaterialTheme.colorScheme.primary.copy(alpha = alpha.value),
            ),
            modifier = modifier
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .alpha(alpha.value),
        )
    }
}
