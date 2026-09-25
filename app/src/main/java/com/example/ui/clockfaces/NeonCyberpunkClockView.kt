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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CurrentTimeState
import com.example.model.ClockPreferencesState
import kotlin.math.min

/**
 * Cyber Glow Neon Clock Face:
 * Realistically renders glass gas-discharge neon tubes with multi-stage glow,
 * plasma core, glass tube reflection, and subtle gas flicker.
 */
@Composable
fun NeonCyberpunkClockView(
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

    val neonColor = if (isNight) palette.primary.copy(alpha = 0.55f) else palette.primary
    val glowColor = if (isNight) palette.glow.copy(alpha = 0.25f) else palette.glow

    // Micro flicker for living neon tube realism
    val infiniteTransition = rememberInfiniteTransition(label = "neon_flicker")
    val flickerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("neon_cyberpunk_clock_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Top Header: Prominent Date and Day of Week
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x33000000))
                .border(1.dp, neonColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = timeState.formattedDateFullJa,
                color = if (isNight) Color(0xFFB0B0C0) else Color(0xFFE8E8FF),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "NEON GLOW",
                color = neonColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Neon Numerals
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            val showSec = preferences.showSeconds
            val digitCount = if (showSec) 6 else 4
            val colonCount = if (showSec) 2 else 1

            // Dynamic scale: maximize on 1024x600
            val totalUnits = digitCount * 1.0f + colonCount * 0.35f + (digitCount - 1) * 0.15f
            val digitWidth = min(maxWidth.value / totalUnits, maxHeight.value / 1.7f).dp
            val digitHeight = digitWidth * 1.75f
            val colonWidth = digitWidth * 0.35f

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Hours
                NeonDigit(char = hStr[0], width = digitWidth, height = digitHeight, neonColor = neonColor, glowColor = glowColor, alpha = flickerAlpha)
                Spacer(modifier = Modifier.width(digitWidth * 0.12f))
                NeonDigit(char = hStr[1], width = digitWidth, height = digitHeight, neonColor = neonColor, glowColor = glowColor, alpha = flickerAlpha)

                // Colon
                NeonColon(width = colonWidth, height = digitHeight, neonColor = neonColor, glowColor = glowColor, alpha = flickerAlpha)

                // Minutes
                NeonDigit(char = mStr[0], width = digitWidth, height = digitHeight, neonColor = neonColor, glowColor = glowColor, alpha = flickerAlpha)
                Spacer(modifier = Modifier.width(digitWidth * 0.12f))
                NeonDigit(char = mStr[1], width = digitWidth, height = digitHeight, neonColor = neonColor, glowColor = glowColor, alpha = flickerAlpha)

                // Seconds
                if (showSec) {
                    NeonColon(width = colonWidth, height = digitHeight, neonColor = neonColor, glowColor = glowColor, alpha = flickerAlpha)
                    NeonDigit(char = sStr[0], width = digitWidth * 0.85f, height = digitHeight * 0.85f, neonColor = neonColor, glowColor = glowColor, alpha = flickerAlpha)
                    Spacer(modifier = Modifier.width(digitWidth * 0.10f))
                    NeonDigit(char = sStr[1], width = digitWidth * 0.85f, height = digitHeight * 0.85f, neonColor = neonColor, glowColor = glowColor, alpha = flickerAlpha)
                }
            }
        }

        if (!preferences.is24Hour) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (timeState.isPm) "P.M." else "A.M.",
                color = neonColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
        }
    }
}

@Composable
private fun NeonDigit(
    char: Char,
    width: Dp,
    height: Dp,
    neonColor: Color,
    glowColor: Color,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(width, height)
            .padding(2.dp)
    ) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.11f

        // Draw unlit dark glass tube shadow
        drawDigitPaths(char, w, h, strokeW * 1.5f, Color(0x22000000), Color(0x22000000))

        // Stage 1: Ambient outer halo glow
        drawDigitPaths(char, w, h, strokeW * 2.8f, glowColor.copy(alpha = 0.18f * alpha), glowColor.copy(alpha = 0.18f * alpha))

        // Stage 2: Concentrated medium neon aura
        drawDigitPaths(char, w, h, strokeW * 1.6f, neonColor.copy(alpha = 0.45f * alpha), neonColor.copy(alpha = 0.45f * alpha))

        // Stage 3: Colored glass tube body
        drawDigitPaths(char, w, h, strokeW * 0.9f, neonColor.copy(alpha = 0.90f * alpha), neonColor.copy(alpha = 0.90f * alpha))

        // Stage 4: Super-hot white core discharge
        drawDigitPaths(char, w, h, strokeW * 0.35f, Color.White.copy(alpha = 0.95f * alpha), Color.White.copy(alpha = 0.95f * alpha))
    }
}

@Composable
private fun NeonColon(
    width: Dp,
    height: Dp,
    neonColor: Color,
    glowColor: Color,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width, height)) {
        val cx = size.width / 2f
        val dotR = size.width * 0.22f
        val y1 = size.height * 0.35f
        val y2 = size.height * 0.65f

        for (cy in listOf(y1, y2)) {
            // Outer halo
            drawCircle(glowColor.copy(alpha = 0.25f * alpha), radius = dotR * 3f, center = Offset(cx, cy))
            // Neon aura
            drawCircle(neonColor.copy(alpha = 0.6f * alpha), radius = dotR * 1.8f, center = Offset(cx, cy))
            // Tube body
            drawCircle(neonColor.copy(alpha = 0.95f * alpha), radius = dotR, center = Offset(cx, cy))
            // White core
            drawCircle(Color.White.copy(alpha = 0.95f * alpha), radius = dotR * 0.45f, center = Offset(cx, cy))
        }
    }
}

private fun DrawScope.drawDigitPaths(
    char: Char,
    w: Float,
    h: Float,
    strokeWidth: Float,
    color: Color,
    coreColor: Color
) {
    val pad = strokeWidth * 0.7f
    val l = pad
    val r = w - pad
    val t = pad
    val b = h - pad
    val m = h / 2f

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
            line(r - (r - l) * 0.35f, t + (m - t) * 0.3f, r, t)
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
            lineTo((l + r) * 0.6f, m)
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
            lineTo(l + (r - l) * 0.2f, b)
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
