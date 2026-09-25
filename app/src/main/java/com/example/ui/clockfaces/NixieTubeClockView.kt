package com.example.ui.clockfaces

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CurrentTimeState
import com.example.model.ClockPreferencesState
import kotlin.math.min

/**
 * Vintage Nixie Vacuum Tube Clock Face:
 * Simulates cold-cathode gas-discharge vacuum tubes (IN-18 style) with fine mesh anode grid,
 * glass tube envelope highlights, and warm glowing neon filaments.
 */
@Composable
fun NixieTubeClockView(
    timeState: CurrentTimeState,
    preferences: ClockPreferencesState,
    modifier: Modifier = Modifier
) {
    val palette = preferences.colorPalette
    val isNight = preferences.isNightMode

    val hourVal = if (preferences.is24Hour) timeState.hour24 else timeState.hour12
    val hStr = hourVal.toString().padStart(2, '0')
    val mStr = timeState.minute.toString().padStart(2, '0')
    val sStr = timeState.second.toString().padStart(2, '0')

    // Classic Nixie warm amber/orange with blue plasma halo
    val filamentColor = if (isNight) Color(0xFFFF7A00).copy(alpha = 0.6f) else Color(0xFFFF9E1B)
    val glowColor = if (isNight) Color(0xFFFF5500).copy(alpha = 0.25f) else Color(0xFFFF6600)
    val coreColor = if (isNight) Color(0xFFFFF0D0).copy(alpha = 0.8f) else Color(0xFFFFF7EA)

    val infiniteTransition = rememberInfiniteTransition(label = "nixie_glow")
    val tubeShimmer by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("nixie_tube_clock_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Date Header
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x55110E0A))
                .border(1.2.dp, Color(0x44FF9E1B), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = timeState.formattedDateFullJa,
                color = Color(0xFFFFE0B2),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "VINTAGE NIXIE IN-18",
                color = Color(0xFFFFB74D),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Nixie Tube Array
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            val showSec = preferences.showSeconds
            val tubeCount = if (showSec) 6 else 4
            val colonCount = if (showSec) 2 else 1

            val totalUnits = tubeCount * 1.0f + colonCount * 0.38f + (tubeCount - 1) * 0.16f
            val tubeWidth = min(maxWidth.value / totalUnits, maxHeight.value / 1.65f).dp
            val tubeHeight = tubeWidth * 1.70f
            val colonWidth = tubeWidth * 0.38f

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Hour Tubes
                NixieTube(char = hStr[0], width = tubeWidth, height = tubeHeight, filamentColor = filamentColor, glowColor = glowColor, coreColor = coreColor, shimmer = tubeShimmer)
                Spacer(modifier = Modifier.width(tubeWidth * 0.12f))
                NixieTube(char = hStr[1], width = tubeWidth, height = tubeHeight, filamentColor = filamentColor, glowColor = glowColor, coreColor = coreColor, shimmer = tubeShimmer)

                // Colon Tube
                NixieColonTube(width = colonWidth, height = tubeHeight, filamentColor = filamentColor, glowColor = glowColor, coreColor = coreColor, shimmer = tubeShimmer)

                // Minute Tubes
                NixieTube(char = mStr[0], width = tubeWidth, height = tubeHeight, filamentColor = filamentColor, glowColor = glowColor, coreColor = coreColor, shimmer = tubeShimmer)
                Spacer(modifier = Modifier.width(tubeWidth * 0.12f))
                NixieTube(char = mStr[1], width = tubeWidth, height = tubeHeight, filamentColor = filamentColor, glowColor = glowColor, coreColor = coreColor, shimmer = tubeShimmer)

                // Seconds Tubes
                if (showSec) {
                    NixieColonTube(width = colonWidth, height = tubeHeight, filamentColor = filamentColor, glowColor = glowColor, coreColor = coreColor, shimmer = tubeShimmer)
                    NixieTube(char = sStr[0], width = tubeWidth * 0.88f, height = tubeHeight * 0.88f, filamentColor = filamentColor, glowColor = glowColor, coreColor = coreColor, shimmer = tubeShimmer)
                    Spacer(modifier = Modifier.width(tubeWidth * 0.10f))
                    NixieTube(char = sStr[1], width = tubeWidth * 0.88f, height = tubeHeight * 0.88f, filamentColor = filamentColor, glowColor = glowColor, coreColor = coreColor, shimmer = tubeShimmer)
                }
            }
        }

        if (!preferences.is24Hour) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (timeState.isPm) "● PM INDICATOR" else "○ AM INDICATOR",
                color = Color(0xFFFFB74D),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun NixieTube(
    char: Char,
    width: Dp,
    height: Dp,
    filamentColor: Color,
    glowColor: Color,
    coreColor: Color,
    shimmer: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(width, height)
            .padding(horizontal = 2.dp)
    ) {
        val w = size.width
        val h = size.height
        val cornerRadius = CornerRadius(w * 0.35f, w * 0.35f)

        // 1. Dark Glass Vacuum Envelope background
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1E1710), Color(0xFF0F0C08), Color(0xFF16120C))
            ),
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = cornerRadius
        )

        // 2. Anode mesh grid dots inside tube
        val meshStep = w * 0.11f
        var mx = meshStep
        while (mx < w - meshStep) {
            var my = meshStep * 1.5f
            while (my < h - meshStep * 1.5f) {
                drawCircle(
                    color = Color(0x18FFCC80),
                    radius = 1.0.dp.toPx(),
                    center = Offset(mx, my)
                )
                my += meshStep
            }
            mx += meshStep
        }

        // 3. Filament Glow Layers
        val strokeW = w * 0.10f

        // Wide amber gas glow
        drawNixieFilament(char, w, h, strokeW * 2.5f, glowColor.copy(alpha = 0.22f * shimmer))
        // Saturated orange glow
        drawNixieFilament(char, w, h, strokeW * 1.4f, filamentColor.copy(alpha = 0.65f * shimmer))
        // Sharp filament wire
        drawNixieFilament(char, w, h, strokeW * 0.75f, filamentColor.copy(alpha = 0.95f * shimmer))
        // White-hot filament center
        drawNixieFilament(char, w, h, strokeW * 0.30f, coreColor.copy(alpha = 0.95f * shimmer))

        // 4. Glass tube rim & specular reflection
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0x33FFFFFF),
                    Color(0x05FFFFFF),
                    Color(0x00FFFFFF),
                    Color(0x1AFFFFFF)
                )
            ),
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.8.dp.toPx())
        )

        // Glass vertical highlight streak
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x00FFFFFF), Color(0x28FFFFFF), Color(0x00FFFFFF))
            ),
            start = Offset(w * 0.16f, h * 0.15f),
            end = Offset(w * 0.16f, h * 0.85f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Metallic Tube Base socket
        drawRect(
            color = Color(0xFF2B2015),
            topLeft = Offset(w * 0.15f, h - 6.dp.toPx()),
            size = Size(w * 0.7f, 6.dp.toPx())
        )
    }
}

@Composable
private fun NixieColonTube(
    width: Dp,
    height: Dp,
    filamentColor: Color,
    glowColor: Color,
    coreColor: Color,
    shimmer: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(width, height)
            .padding(horizontal = 2.dp)
    ) {
        val w = size.width
        val h = size.height
        val cornerRadius = CornerRadius(w * 0.45f, w * 0.45f)

        // Glass background
        drawRoundRect(
            color = Color(0xFF14100C),
            topLeft = Offset(0f, h * 0.2f),
            size = Size(w, h * 0.6f),
            cornerRadius = cornerRadius
        )

        val cx = w / 2f
        val r = w * 0.22f
        val y1 = h * 0.38f
        val y2 = h * 0.62f

        for (cy in listOf(y1, y2)) {
            // Glow
            drawCircle(glowColor.copy(alpha = 0.30f * shimmer), radius = r * 3.0f, center = Offset(cx, cy))
            drawCircle(filamentColor.copy(alpha = 0.75f * shimmer), radius = r * 1.5f, center = Offset(cx, cy))
            // Core
            drawCircle(coreColor.copy(alpha = 0.95f * shimmer), radius = r * 0.6f, center = Offset(cx, cy))
        }

        // Glass outline
        drawRoundRect(
            color = Color(0x22FFFFFF),
            topLeft = Offset(0f, h * 0.2f),
            size = Size(w, h * 0.6f),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

private fun DrawScope.drawNixieFilament(
    char: Char,
    w: Float,
    h: Float,
    strokeWidth: Float,
    color: Color
) {
    val pad = strokeWidth * 0.8f + w * 0.12f
    val l = pad
    val r = w - pad
    val t = pad + h * 0.08f
    val b = h - pad - h * 0.08f
    val m = (t + b) / 2f

    val stroke = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }

    fun path(builder: Path.() -> Unit) {
        val p = Path().apply(builder)
        drawPath(p, color, style = stroke)
    }

    when (char) {
        '0' -> path {
            moveTo(l, t + (m - t) * 0.5f)
            lineTo(l, b - (b - m) * 0.5f)
            quadraticBezierTo(l, b, (l + r) / 2f, b)
            quadraticBezierTo(r, b, r, b - (b - m) * 0.5f)
            lineTo(r, t + (m - t) * 0.5f)
            quadraticBezierTo(r, t, (l + r) / 2f, t)
            quadraticBezierTo(l, t, l, t + (m - t) * 0.5f)
            close()
        }
        '1' -> {
            line(r, t, r, b)
            line(r - (r - l) * 0.4f, t + (m - t) * 0.3f, r, t)
        }
        '2' -> path {
            moveTo(l, t + (m - t) * 0.4f)
            quadraticBezierTo(l, t, (l + r) / 2f, t)
            quadraticBezierTo(r, t, r, t + (m - t) * 0.7f)
            lineTo(l, b)
            lineTo(r, b)
        }
        '3' -> path {
            moveTo(l, t)
            lineTo(r, t)
            lineTo((l + r) * 0.65f, m)
            lineTo(r, m + (b - m) * 0.2f)
            quadraticBezierTo(r, b, (l + r) / 2f, b)
            quadraticBezierTo(l, b, l, b - (b - m) * 0.4f)
        }
        '4' -> path {
            moveTo(r - (r - l) * 0.2f, b)
            lineTo(r - (r - l) * 0.2f, t)
            lineTo(l, m)
            lineTo(r, m)
        }
        '5' -> path {
            moveTo(r, t)
            lineTo(l, t)
            lineTo(l, m)
            quadraticBezierTo(r, m - 5f, r, m + (b - m) * 0.5f)
            quadraticBezierTo(r, b, (l + r) / 2f, b)
            quadraticBezierTo(l, b, l, b - (b - m) * 0.3f)
        }
        '6' -> path {
            moveTo(r, t + (m - t) * 0.3f)
            quadraticBezierTo((l + r) / 2f, t, l, m)
            lineTo(l, b - (b - m) * 0.4f)
            quadraticBezierTo(l, b, (l + r) / 2f, b)
            quadraticBezierTo(r, b, r, m + (b - m) * 0.5f)
            quadraticBezierTo(r, m, l, m)
        }
        '7' -> path {
            moveTo(l, t)
            lineTo(r, t)
            lineTo(l + (r - l) * 0.25f, b)
        }
        '8' -> {
            path {
                moveTo((l + r) / 2f, t)
                quadraticBezierTo(r, t, r, (t + m) / 2f)
                quadraticBezierTo(r, m, (l + r) / 2f, m)
                quadraticBezierTo(l, m, l, (t + m) / 2f)
                quadraticBezierTo(l, t, (l + r) / 2f, t)
                close()
            }
            path {
                moveTo((l + r) / 2f, m)
                quadraticBezierTo(r, m, r, (m + b) / 2f)
                quadraticBezierTo(r, b, (l + r) / 2f, b)
                quadraticBezierTo(l, b, l, (m + b) / 2f)
                quadraticBezierTo(l, m, (l + r) / 2f, m)
                close()
            }
        }
        '9' -> path {
            moveTo(l, b - (b - m) * 0.3f)
            quadraticBezierTo((l + r) / 2f, b, r, m)
            lineTo(r, t + (m - t) * 0.4f)
            quadraticBezierTo(r, t, (l + r) / 2f, t)
            quadraticBezierTo(l, t, l, t + (m - t) * 0.5f)
            quadraticBezierTo(l, m, r, m)
        }
    }
}
