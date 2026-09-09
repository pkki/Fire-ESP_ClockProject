package com.example.ui.clockfaces

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CurrentTimeState
import com.example.model.ClockPreferencesState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun SwissAnalogClockView(
    timeState: CurrentTimeState,
    preferences: ClockPreferencesState,
    modifier: Modifier = Modifier
) {
    val palette = preferences.colorPalette
    val isNight = preferences.isNightMode

    // Smooth sweeping second hand or ticking
    val secondRatio = (timeState.second + timeState.millisecond / 1000f) / 60f
    val minuteRatio = (timeState.minute + secondRatio) / 60f
    val hourVal = if (preferences.is24Hour) timeState.hour24 % 12 else timeState.hour12 % 12
    val hourRatio = (hourVal + minuteRatio) / 12f

    val dialColor = if (isNight) Color(0xFF070708) else Color(0xFF0A0A0C)
    val ringColor = if (isNight) palette.primary.copy(alpha = 0.15f) else Color(0xFF1E1E24)
    val accentColor = if (isNight) palette.primary.copy(alpha = 0.6f) else palette.primary
    val handColor = if (isNight) Color(0xFFCCCCCC) else Color(0xFFF0F0F5)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("swiss_analog_clock_view"),
        contentAlignment = Alignment.Center
    ) {
        val clockDiameter = min(maxWidth.value, maxHeight.value).dp * 0.90f

        Canvas(modifier = Modifier.size(clockDiameter)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f * 0.94f

            // Outer subtle bezel ring
            drawCircle(
                color = dialColor,
                radius = radius,
                center = center
            )
            drawCircle(
                color = ringColor,
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw 60 minute ticks and 12 hour batons
            for (i in 0 until 60) {
                val angleDeg = i * 6f - 90f
                val angleRad = (angleDeg * PI / 180.0).toFloat()
                val isHour = (i % 5 == 0)
                val tickLen = if (isHour) radius * 0.12f else radius * 0.05f
                val tickWidth = if (isHour) 3.5.dp.toPx() else 1.2.dp.toPx()
                val tickColor = if (isHour) {
                    if (isNight) accentColor.copy(alpha = 0.5f) else Color(0xFFE5E5EB)
                } else {
                    if (isNight) Color(0xFF222226) else Color(0xFF383842)
                }

                val start = Offset(
                    center.x + (radius - tickLen) * cos(angleRad),
                    center.y + (radius - tickLen) * sin(angleRad)
                )
                val end = Offset(
                    center.x + (radius - 2.dp.toPx()) * cos(angleRad),
                    center.y + (radius - 2.dp.toPx()) * sin(angleRad)
                )
                drawLine(
                    color = tickColor,
                    start = start,
                    end = end,
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )
            }

            // Draw Minimalist Date Window at 3 o'clock position
            val dateBoxX = center.x + radius * 0.44f
            val dateBoxY = center.y - 12.dp.toPx()
            val dateBoxW = 44.dp.toPx()
            val dateBoxH = 24.dp.toPx()

            drawRoundRect(
                color = if (isNight) Color(0xFF141416) else Color(0xFF181820),
                topLeft = Offset(dateBoxX, dateBoxY),
                size = Size(dateBoxW, dateBoxH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )
            drawRoundRect(
                color = if (isNight) Color(0xFF2A2A30) else Color(0xFF353545),
                topLeft = Offset(dateBoxX, dateBoxY),
                size = Size(dateBoxW, dateBoxH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Date text inside date box
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = if (isNight) android.graphics.Color.GRAY else android.graphics.Color.WHITE
                    textSize = 13.sp.toPx()
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawText(
                    "${timeState.day}",
                    dateBoxX + dateBoxW / 2f,
                    dateBoxY + dateBoxH / 2f + 5.dp.toPx(),
                    paint
                )
            }

            // Sub-dial branding / metadata text at 6 o'clock and 12 o'clock
            drawContext.canvas.nativeCanvas.apply {
                val subPaint = android.graphics.Paint().apply {
                    color = accentColor.copy(alpha = if (isNight) 0.35f else 0.7f).hashCode()
                    textSize = 11.sp.toPx()
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
                    textAlign = android.graphics.Paint.Align.CENTER
                    letterSpacing = 0.18f
                    isAntiAlias = true
                }
                drawText(
                    "CHRONO SWISS",
                    center.x,
                    center.y - radius * 0.42f,
                    subPaint
                )
                val timeStr = String.format(
                    java.util.Locale.US,
                    "%02d:%02d:%02d",
                    timeState.hour24,
                    timeState.minute,
                    timeState.second
                )
                drawText(
                    timeStr,
                    center.x,
                    center.y + radius * 0.50f,
                    subPaint
                )
            }

            // Hour Hand
            val hourAngleRad = (hourRatio * 360f - 90f) * (PI / 180.0).toFloat()
            val hourLen = radius * 0.54f
            val hourEnd = Offset(
                center.x + hourLen * cos(hourAngleRad),
                center.y + hourLen * sin(hourAngleRad)
            )
            drawLine(
                color = handColor,
                start = center,
                end = hourEnd,
                strokeWidth = 6.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Minute Hand
            val minAngleRad = (minuteRatio * 360f - 90f) * (PI / 180.0).toFloat()
            val minLen = radius * 0.78f
            val minEnd = Offset(
                center.x + minLen * cos(minAngleRad),
                center.y + minLen * sin(minAngleRad)
            )
            drawLine(
                color = handColor,
                start = center,
                end = minEnd,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Center pivot outer cap
            drawCircle(
                color = handColor,
                radius = 7.dp.toPx(),
                center = center
            )

            // Second Hand (Hairline with accent color and counterweight)
            if (preferences.showSeconds) {
                val secAngleRad = (secondRatio * 360f - 90f) * (PI / 180.0).toFloat()
                val secLen = radius * 0.88f
                val counterLen = radius * 0.22f
                val secEnd = Offset(
                    center.x + secLen * cos(secAngleRad),
                    center.y + secLen * sin(secAngleRad)
                )
                val counterEnd = Offset(
                    center.x - counterLen * cos(secAngleRad),
                    center.y - counterLen * sin(secAngleRad)
                )

                // Counterweight tail
                drawLine(
                    color = accentColor,
                    start = counterEnd,
                    end = secEnd,
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Counterweight disc
                drawCircle(
                    color = accentColor,
                    radius = 4.5.dp.toPx(),
                    center = counterEnd
                )
                // Center accent cap
                drawCircle(
                    color = accentColor,
                    radius = 4.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = Color(0xFF000000),
                    radius = 1.5.dp.toPx(),
                    center = center
                )
            }
        }
    }
}
