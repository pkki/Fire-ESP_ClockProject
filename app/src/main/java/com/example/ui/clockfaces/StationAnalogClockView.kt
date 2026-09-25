package com.example.ui.clockfaces

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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

/**
 * Classic European Railroad Station Master Clock:
 * High-legibility dial with bold Arabic numerals 1-12, heavy baton hands,
 * and the iconic red lollipop seconds hand with smooth circular disc.
 */
@Composable
fun StationAnalogClockView(
    timeState: CurrentTimeState,
    preferences: ClockPreferencesState,
    modifier: Modifier = Modifier
) {
    val palette = preferences.colorPalette
    val isNight = preferences.isNightMode

    val hourVal = timeState.hour24 % 12
    val minuteRatio = (timeState.minute + timeState.second / 60f) / 60f
    val hourRatio = (hourVal + minuteRatio) / 12f
    val secondRatio = timeState.second / 60f

    val dialBgColor = if (isNight) Color(0xFF0D0D10) else Color(0xFFFAFAFD)
    val rimColor = if (isNight) Color(0xFF24242C) else Color(0xFF1B1B22)
    val numeralColor = if (isNight) Color(0xFFDCDCE6) else Color(0xFF141418)
    val handColor = if (isNight) Color(0xFFEEEEF4) else Color(0xFF111116)
    val secondHandColor = Color(0xFFE53935) // Iconic railroad red

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 10.dp)
            .testTag("station_analog_clock_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Date Banner Header
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
                color = if (isNight) Color(0xFFCCCCD8) else Color(0xFF222228),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "RAILWAY MASTER",
                color = secondHandColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Large Analog Dial
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            // Maximize diameter to fill available height
            val clockDiameter = min(maxWidth.value, maxHeight.value).dp * 0.95f

            Canvas(modifier = Modifier.size(clockDiameter)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f * 0.95f

                // Dial Face Base
                drawCircle(color = dialBgColor, radius = radius, center = center)

                // Bezel outer ring
                drawCircle(
                    color = rimColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 8.dp.toPx())
                )
                drawCircle(
                    color = rimColor.copy(alpha = 0.25f),
                    radius = radius - 6.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Minute Chapter Ring & Ticks
                for (i in 0 until 60) {
                    val angleDeg = i * 6f - 90f
                    val angleRad = (angleDeg * PI / 180.0).toFloat()
                    val isHour = (i % 5 == 0)
                    val tickLen = if (isHour) radius * 0.12f else radius * 0.05f
                    val tickWidth = if (isHour) 4.0.dp.toPx() else 1.8.dp.toPx()
                    val tickColor = if (isHour) numeralColor else numeralColor.copy(alpha = 0.45f)

                    val start = Offset(
                        center.x + (radius - tickLen - 6.dp.toPx()) * cos(angleRad),
                        center.y + (radius - tickLen - 6.dp.toPx()) * sin(angleRad)
                    )
                    val end = Offset(
                        center.x + (radius - 6.dp.toPx()) * cos(angleRad),
                        center.y + (radius - 6.dp.toPx()) * sin(angleRad)
                    )
                    drawLine(
                        color = tickColor,
                        start = start,
                        end = end,
                        strokeWidth = tickWidth,
                        cap = StrokeCap.Square
                    )
                }

                // Draw Bold Arabic Numerals 1..12 using native Canvas
                val textRadius = radius * 0.73f
                val paint = Paint().apply {
                    color = numeralColor.toArgb()
                    textSize = radius * 0.19f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    isAntiAlias = true
                }

                val fontMetrics = paint.fontMetrics
                val textYOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f

                for (hour in 1..12) {
                    val angleDeg = hour * 30f - 90f
                    val angleRad = (angleDeg * PI / 180.0).toFloat()
                    val x = (center.x + textRadius * cos(angleRad.toDouble())).toFloat()
                    val y = (center.y + textRadius * sin(angleRad.toDouble()) - textYOffset).toFloat()

                    drawContext.canvas.nativeCanvas.drawText(
                        hour.toString(),
                        x,
                        y,
                        paint
                    )
                }

                // Hour Hand (Heavy Baton with counterweight)
                val hourAngleDeg = hourRatio * 360f - 90f
                val hourAngleRad = (hourAngleDeg * PI / 180.0).toFloat()
                val hourHandLen = radius * 0.54f
                val hourCounterLen = radius * 0.14f

                drawLine(
                    color = handColor,
                    start = Offset(
                        (center.x - hourCounterLen * cos(hourAngleRad.toDouble())).toFloat(),
                        (center.y - hourCounterLen * sin(hourAngleRad.toDouble())).toFloat()
                    ),
                    end = Offset(
                        (center.x + hourHandLen * cos(hourAngleRad.toDouble())).toFloat(),
                        (center.y + hourHandLen * sin(hourAngleRad.toDouble())).toFloat()
                    ),
                    strokeWidth = 9.dp.toPx(),
                    cap = StrokeCap.Square
                )

                // Minute Hand (Longer Baton with counterweight)
                val minuteAngleDeg = minuteRatio * 360f - 90f
                val minuteAngleRad = (minuteAngleDeg * PI / 180.0).toFloat()
                val minHandLen = radius * 0.82f
                val minCounterLen = radius * 0.16f

                drawLine(
                    color = handColor,
                    start = Offset(
                        (center.x - minCounterLen * cos(minuteAngleRad.toDouble())).toFloat(),
                        (center.y - minCounterLen * sin(minuteAngleRad.toDouble())).toFloat()
                    ),
                    end = Offset(
                        (center.x + minHandLen * cos(minuteAngleRad.toDouble())).toFloat(),
                        (center.y + minHandLen * sin(minuteAngleRad.toDouble())).toFloat()
                    ),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Square
                )

                // Second Hand (Red Lollipop Hand with round disc pointer)
                if (preferences.showSeconds) {
                    val secAngleDeg = secondRatio * 360f - 90f
                    val secAngleRad = (secAngleDeg * PI / 180.0).toFloat()
                    val secHandLen = radius * 0.84f
                    val secCounterLen = radius * 0.22f
                    val discRadius = radius * 0.08f
                    val discDist = radius * 0.65f

                    // Red needle line
                    drawLine(
                        color = secondHandColor,
                        start = Offset(
                            (center.x - secCounterLen * cos(secAngleRad.toDouble())).toFloat(),
                            (center.y - secCounterLen * sin(secAngleRad.toDouble())).toFloat()
                        ),
                        end = Offset(
                            (center.x + secHandLen * cos(secAngleRad.toDouble())).toFloat(),
                            (center.y + secHandLen * sin(secAngleRad.toDouble())).toFloat()
                        ),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Iconic Lollipop Round Disc
                    val discCenter = Offset(
                        (center.x + discDist * cos(secAngleRad.toDouble())).toFloat(),
                        (center.y + discDist * sin(secAngleRad.toDouble())).toFloat()
                    )
                    drawCircle(
                        color = secondHandColor,
                        radius = discRadius,
                        center = discCenter
                    )
                }

                // Center Cap
                drawCircle(color = handColor, radius = 7.dp.toPx(), center = center)
                if (preferences.showSeconds) {
                    drawCircle(color = secondHandColor, radius = 4.dp.toPx(), center = center)
                }
            }
        }
    }
}
