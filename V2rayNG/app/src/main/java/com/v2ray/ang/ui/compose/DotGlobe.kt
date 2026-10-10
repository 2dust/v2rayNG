package com.v2ray.ang.ui.compose

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val NODE_COUNT = 320
private const val HUB_COUNT = 14
private const val ARC_COUNT = 7
private const val ARC_STEPS = 30
private const val MERIDIANS = 12
private const val PARALLELS = 5
private const val LINE_STEPS = 48

/** Tilt of the globe's axis toward the viewer, about 22 degrees. */
private const val TILT = 0.38f

/**
 * A network-style globe: an even scatter of nodes (not the real map of the Earth), a faint latitude/longitude grid and
 * glowing arcs that connect a few hub nodes, with a light pulse running along every arc. All geometry is built once
 * for the whole process and shared by the splash screen and the main screen.
 */
private class GlobeGeometry(
    /** x, y, z of every node, packed. */
    val nodes: FloatArray,
    /** Grid lines, each a packed run of x, y, z points. */
    val grid: List<FloatArray>,
    /** Arcs between hub nodes, lifted off the surface, each a packed run of x, y, z points. */
    val arcs: List<FloatArray>,
)

private val globeGeometry: GlobeGeometry by lazy(LazyThreadSafetyMode.PUBLICATION) { buildGlobeGeometry() }

private fun buildGlobeGeometry(): GlobeGeometry {
    // Fibonacci sphere: an even scatter, with no empty patches and no clumps at the poles.
    val nodes = FloatArray(NODE_COUNT * 3)
    val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
    for (i in 0 until NODE_COUNT) {
        val y = 1f - (i + 0.5f) / NODE_COUNT * 2f
        val ring = sqrt((1f - y * y).coerceAtLeast(0f))
        val theta = golden * i
        nodes[i * 3] = cos(theta) * ring
        nodes[i * 3 + 1] = y
        nodes[i * 3 + 2] = sin(theta) * ring
    }

    val grid = ArrayList<FloatArray>()
    for (m in 0 until MERIDIANS) {
        val lon = (2.0 * PI * m / MERIDIANS).toFloat()
        val line = FloatArray((LINE_STEPS + 1) * 3)
        for (s in 0..LINE_STEPS) {
            val lat = (-PI / 2.0 + PI * s / LINE_STEPS).toFloat()
            line[s * 3] = cos(lat) * sin(lon)
            line[s * 3 + 1] = sin(lat)
            line[s * 3 + 2] = cos(lat) * cos(lon)
        }
        grid.add(line)
    }
    for (p in 1..PARALLELS) {
        val lat = (-PI / 2.0 + PI * p / (PARALLELS + 1)).toFloat()
        val line = FloatArray((LINE_STEPS + 1) * 3)
        for (s in 0..LINE_STEPS) {
            val lon = (2.0 * PI * s / LINE_STEPS).toFloat()
            line[s * 3] = cos(lat) * sin(lon)
            line[s * 3 + 1] = sin(lat)
            line[s * 3 + 2] = cos(lat) * cos(lon)
        }
        grid.add(line)
    }

    // Arcs between hubs: hub k and hub (k * 5 + 3) mod HUB_COUNT, a fixed pattern so the picture is the same every run.
    val arcs = ArrayList<FloatArray>()
    for (k in 0 until ARC_COUNT) {
        val a = (k * 2) % HUB_COUNT
        val b = (k * 5 + 3) % HUB_COUNT
        if (a == b) continue
        val ax = nodes[a * 3]; val ay = nodes[a * 3 + 1]; val az = nodes[a * 3 + 2]
        val bx = nodes[b * 3]; val by = nodes[b * 3 + 1]; val bz = nodes[b * 3 + 2]
        val dot = (ax * bx + ay * by + az * bz).coerceIn(-0.999f, 0.999f)
        val omega = acos(dot)
        val sinO = sin(omega)
        val arc = FloatArray((ARC_STEPS + 1) * 3)
        for (s in 0..ARC_STEPS) {
            val t = s.toFloat() / ARC_STEPS
            val wa = sin((1f - t) * omega) / sinO
            val wb = sin(t * omega) / sinO
            // Higher in the middle, so a long arc leaves the surface like a flight path.
            val lift = 1f + 0.22f * sin(PI.toFloat() * t) * (omega / PI.toFloat())
            arc[s * 3] = (ax * wa + bx * wb) * lift
            arc[s * 3 + 1] = (ay * wa + by * wb) * lift
            arc[s * 3 + 2] = (az * wa + bz * wb) * lift
        }
        arcs.add(arc)
    }
    return GlobeGeometry(nodes, grid, arcs)
}

/**
 * Turning network globe in the brand colors, drawn in one Canvas pass. The angle is only read while drawing, so a
 * frame redraws the canvas without recomposing anything.
 *
 * @param radiusFraction globe radius as a fraction of the smaller side of the canvas
 * @param periodMs time of one full turn
 * @param spinning false keeps the globe still and the arcs without a running pulse
 * @param dotColor color of the nodes and the arc pulses
 * @param ringColor color of the rim, the grid, the halo and the arcs
 */
@Composable
fun DotGlobe(
    modifier: Modifier = Modifier,
    radiusFraction: Float = 0.46f,
    periodMs: Int = 24000,
    spinning: Boolean = true,
    dotColor: Color = Color(0xFFB9A8FF),
    ringColor: Color = Color(0xFF7C5CFF),
) {
    val geometry = remember { globeGeometry }
    val scratch = remember { FloatArray(3) }
    val transition = rememberInfiniteTransition(label = "globe")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "turn",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "pulse",
    )

    Canvas(modifier = modifier) {
        val r = size.minDimension * radiusFraction
        val c = Offset(size.width / 2f, size.height * 0.5f)
        val a = if (spinning) turn else 0.6f
        val phase = if (spinning) pulse else 0f
        val ca = cos(a)
        val sa = sin(a)
        val ct = cos(TILT)
        val st = sin(TILT)

        // Atmosphere: a soft halo outside the rim and a dim body inside it, so the globe reads as a ball and not a flat disc.
        drawCircle(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                0.72f to ringColor.copy(alpha = 0.22f),
                1f to Color.Transparent,
                center = c,
                radius = r * 1.5f,
            ),
            radius = r * 1.5f, center = c,
        )
        drawCircle(
            Brush.radialGradient(
                listOf(ringColor.copy(alpha = 0.04f), ringColor.copy(alpha = 0.16f)),
                center = c,
                radius = r,
            ),
            radius = r, center = c,
        )
        drawCircle(ringColor.copy(alpha = 0.6f), radius = r, center = c, style = Stroke(width = 1.6f))

        // Grid: only the front half is drawn, brighter where it faces the viewer.
        for (line in geometry.grid) {
            var prevX = 0f; var prevY = 0f; var prevZ = 0f
            var havePrev = false
            for (s in 0 until line.size / 3) {
                project(line[s * 3], line[s * 3 + 1], line[s * 3 + 2], ca, sa, ct, st, scratch)
                val x = c.x + scratch[0] * r
                val y = c.y - scratch[1] * r
                val z = scratch[2]
                if (havePrev && z > 0f && prevZ > 0f) {
                    val depth = (z + prevZ) * 0.5f
                    drawLine(
                        ringColor.copy(alpha = 0.05f + 0.20f * depth),
                        Offset(prevX, prevY), Offset(x, y), strokeWidth = 1f,
                    )
                }
                prevX = x; prevY = y; prevZ = z; havePrev = true
            }
        }

        // Nodes: bigger and brighter toward the viewer; the hubs get a small halo.
        val nodes = geometry.nodes
        for (i in 0 until NODE_COUNT) {
            project(nodes[i * 3], nodes[i * 3 + 1], nodes[i * 3 + 2], ca, sa, ct, st, scratch)
            val z = scratch[2]
            if (z <= 0f) continue
            val center = Offset(c.x + scratch[0] * r, c.y - scratch[1] * r)
            if (i < HUB_COUNT) {
                drawCircle(ringColor.copy(alpha = 0.35f * z), radius = 5.5f + 4f * z, center = center)
                drawCircle(dotColor.copy(alpha = 0.95f), radius = 2.2f + 1.6f * z, center = center)
            } else {
                drawCircle(dotColor.copy(alpha = 0.12f + 0.7f * z), radius = 0.9f + 1.5f * z, center = center)
            }
        }

        // Arcs between hubs, each with a light pulse that runs from one end to the other.
        for ((k, arc) in geometry.arcs.withIndex()) {
            val steps = arc.size / 3 - 1
            val head = (phase + k * 0.19f) % 1f
            val headStep = head * steps
            var prevX = 0f; var prevY = 0f; var prevOk = false
            for (s in 0..steps) {
                project(arc[s * 3], arc[s * 3 + 1], arc[s * 3 + 2], ca, sa, ct, st, scratch)
                val x = c.x + scratch[0] * r
                val y = c.y - scratch[1] * r
                // In front of the globe, or out beyond its edge, where nothing hides it.
                val ok = scratch[2] > 0f || scratch[0] * scratch[0] + scratch[1] * scratch[1] > 1f
                if (prevOk && ok) {
                    val behind = headStep - s
                    val trail = if (spinning && behind >= 0f && behind < 7f) 1f - behind / 7f else 0f
                    // A plain color per segment: a gradient brush would be allocated for every segment on every frame.
                    drawLine(
                        lerpColor(ringColor, dotColor, trail).copy(alpha = 0.22f + 0.78f * trail),
                        Offset(prevX, prevY), Offset(x, y),
                        strokeWidth = 1f + 1.6f * trail, cap = StrokeCap.Round,
                    )
                }
                prevX = x; prevY = y; prevOk = ok
            }
        }
    }
}

/** Rotates a point about the vertical axis by the angle (as [ca], [sa]), tilts it by [TILT] and writes x, y, z to [out]. */
private fun project(x: Float, y: Float, z: Float, ca: Float, sa: Float, ct: Float, st: Float, out: FloatArray) {
    val x1 = x * ca + z * sa
    val z1 = -x * sa + z * ca
    out[0] = x1
    out[1] = y * ct - z1 * st
    out[2] = y * st + z1 * ct
}

private fun lerpColor(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = 1f,
)
