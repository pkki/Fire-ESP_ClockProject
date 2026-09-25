package com.example.ui.clockfaces

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

    // Crisp ticking second hand (1Hz battery-saver)
    val secondRatio = timeState.second / 60f
    val minuteRatio = (timeState.minute + secondRatio) / 60f
    val hourVal = if (preferences.is24Hour) timeState.hour24 % 12 else timeState.hour12 % 12
    val hourRatio = (hourVal + minuteRatio) / 12f

    val dialColor = if (isNight) Color(0xFF070708) else Color(0xFF0A0A0C)
    val ringColor = if (isNight) palette.primary.copy(alpha = 0.25f) else Color(0xFF282832)
    val accentColor = if (isNight) palette.primary.copy(alpha = 0.6f) else palette.primary
    val handColor = if (isNight) Color(0xFFCCCCCC) else Color(0xFFF0F0F5)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
            .testTag("swiss_analog_clock_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Prominent Date Display
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x3314141E))
                .border(1.2.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = timeState.formattedDateFullJa,
                color = if (isNight) Color(0xFFCCCCCC) else Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "SWISS MINIMAL",
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            // Maximize diameter to fill available height on 1024x600
            val clockDiameter = min(maxWidth.value, maxHeight.value).dp * 0.96f

            Canvas(modifier = Modifier.size(clockDiameter)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f * 0.95f

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
                    style = Stroke(width = 4.dp.toPx())
                )

                // Draw 60 minute ticks and 12 hour batons
                for (i in 0 until 60) {
                    val angleDeg = i * 6f - 90f
                    val angleRad = (angleDeg * PI / 180.0).toFloat()
                    val isHour = (i % 5 == 0)
                    val tickLen = if (isHour) radius * 0.14f else radius * 0.06f
                    val tickWidth = if (isHour) 4.5.dp.toPx() else 1.6.dp.toPx()
                    val tickColor = if (isHour) {
                        if (isNight) accentColor.copy(alpha = 0.7f) else Color(0xFFF0F0F5)
                    } else {
                        if (isNight) Color(0xFF222226) else Color(0xFF484855)
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

                // Draw Large Minimalist Date Window at 3 o'clock position
                val dateBoxX = center.x + radius * 0.42f
                val dateBoxY = center.y - 14.dp.toPx()
                val dateBoxW = 52.dp.toPx()
                val dateBoxH = 28.dp.toPx()

                drawRoundRect(
                    color = if (isNight) Color(0xFF141416) else Color(0xFF1C1C24),
                    topLeft = Offset(dateBoxX, dateBoxY),
                    size = Size(dateBoxW, dateBoxH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                )
                drawRoundRect(
                    color = if (isNight) Color(0xFF2A2A30) else accentColor.copy(alpha = 0.5f),
                    topLeft = Offset(dateBoxX, dateBoxY),
                    size = Size(dateBoxW, dateBoxH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // Date text inside date box
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = if (isNight) android.graphics.Color.LTGRAY else android.graphics.Color.WHITE
                        textSize = 15.sp.toPx()
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(
                        "${timeState.day}日",
                        dateBoxX + dateBoxW / 2f,
                        dateBoxY + dateBoxH / 2f + 5.5.dp.toPx(),
                        paint
                    )
                }

                // Sub-dial branding / metadata text at 6 o'clock and 12 o'clock
                drawContext.canvas.nativeCanvas.apply {
                    val subPaint = android.graphics.Paint().apply {
                        color = accentColor.copy(alpha = if (isNight) 0.4f else 0.85f).hashCode()
                        textSize = 12.sp.toPx()
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                        textAlign = android.graphics.Paint.Align.CENTER
                        letterSpacing = 0.20f
                        isAntiAlias = true
                    }
                    drawText(
                        "SWISS CHRONO",
                        center.x,
                        center.y - radius * 0.40f,
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

                // Hour Hand (Bolder)
                val hourAngleRad = ((hourRatio * 360f - 90f) * (PI / 180.0)).toFloat()
                val hourLen = radius * 0.54f
                val hourEnd = Offset(
                    (center.x + hourLen * cos(hourAngleRad.toDouble())).toFloat(),
                    (center.y + hourLen * sin(hourAngleRad.toDouble())).toFloat()
                )
                drawLine(
                    color = handColor,
                    start = center,
                    end = hourEnd,
                    strokeWidth = 8.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Minute Hand (Bolder)
                val minAngleRad = ((minuteRatio * 360f - 90f) * (PI / 180.0)).toFloat()
                val minLen = radius * 0.82f
                val minEnd = Offset(
                    (center.x + minLen * cos(minAngleRad.toDouble())).toFloat(),
                    (center.y + minLen * sin(minAngleRad.toDouble())).toFloat()
                )
                drawLine(
                    color = handColor,
                    start = center,
                    end = minEnd,
                    strokeWidth = 5.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center pivot outer cap
                drawCircle(
                    color = handColor,
                    radius = 9.dp.toPx(),
                    center = center
                )

                // Second Hand (Hairline with accent color and counterweight)
                if (preferences.showSeconds) {
                    val secAngleRad = ((secondRatio * 360f - 90f) * (PI / 180.0)).toFloat()
                    val secLen = radius * 0.88f
                    val counterLen = radius * 0.22f
                    val secEnd = Offset(
                        (center.x + secLen * cos(secAngleRad.toDouble())).toFloat(),
                        (center.y + secLen * sin(secAngleRad.toDouble())).toFloat()
                    )
                    val counterEnd = Offset(
                        (center.x - counterLen * cos(secAngleRad.toDouble())).toFloat(),
                        (center.y - counterLen * sin(secAngleRad.toDouble())).toFloat()
                    )

                    // Counterweight tail
                    drawLine(
                        color = accentColor,
                        start = counterEnd,
                        end = secEnd,
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Counterweight disc
                    drawCircle(
                        color = accentColor,
                        radius = 5.5.dp.toPx(),
                        center = counterEnd
                    )
                    // Center accent cap
                    drawCircle(
                        color = accentColor,
                        radius = 5.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF000000),
                        radius = 2.dp.toPx(),
                        center = center
                    )
                }
            }
        }
    }
}
