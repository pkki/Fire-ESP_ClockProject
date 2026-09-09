package com.example.ui.clockfaces

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CurrentTimeState
import com.example.model.ClockPreferencesState
import kotlin.math.min

@Composable
fun MatrixDotsClockView(
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

    val activeDotColor = if (isNight) palette.primary.copy(alpha = 0.5f) else palette.primary
    val inactiveDotColor = if (isNight) palette.inactiveSegment.copy(alpha = 0.3f) else palette.inactiveSegment

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("matrix_dots_clock_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DOT-MATRIX · ${timeState.timezoneDisplayName}",
            color = activeDotColor.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            val digitCount = if (preferences.showSeconds) 6 else 4
            val colonCount = if (preferences.showSeconds) 2 else 1
            val totalUnits = digitCount * 5f + colonCount * 2f + (digitCount + colonCount) * 1f
            val dotSpacing = min(maxWidth.value / totalUnits, maxHeight.value / 12f).dp

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Hours
                MatrixDigit(char = hStr[0], spacing = dotSpacing, activeColor = activeDotColor, inactiveColor = inactiveDotColor)
                Spacer(modifier = Modifier.width(dotSpacing))
                MatrixDigit(char = hStr[1], spacing = dotSpacing, activeColor = activeDotColor, inactiveColor = inactiveDotColor)

                // Colon
                Spacer(modifier = Modifier.width(dotSpacing * 0.7f))
                MatrixColon(spacing = dotSpacing, activeColor = activeDotColor, inactiveColor = inactiveDotColor)
                Spacer(modifier = Modifier.width(dotSpacing * 0.7f))

                // Minutes
                MatrixDigit(char = mStr[0], spacing = dotSpacing, activeColor = activeDotColor, inactiveColor = inactiveDotColor)
                Spacer(modifier = Modifier.width(dotSpacing))
                MatrixDigit(char = mStr[1], spacing = dotSpacing, activeColor = activeDotColor, inactiveColor = inactiveDotColor)

                if (preferences.showSeconds) {
                    Spacer(modifier = Modifier.width(dotSpacing * 0.7f))
                    MatrixColon(spacing = dotSpacing, activeColor = activeDotColor, inactiveColor = inactiveDotColor)
                    Spacer(modifier = Modifier.width(dotSpacing * 0.7f))

                    MatrixDigit(char = sStr[0], spacing = dotSpacing, activeColor = activeDotColor, inactiveColor = inactiveDotColor)
                    Spacer(modifier = Modifier.width(dotSpacing))
                    MatrixDigit(char = sStr[1], spacing = dotSpacing, activeColor = activeDotColor, inactiveColor = inactiveDotColor)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "${timeState.formattedDateFullEn.uppercase()} · ${timeState.dayOfWeekJa}",
            color = Color(0xFF6E6E7A),
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun MatrixDigit(
    char: Char,
    spacing: Dp,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    val matrix = get5x7Matrix(char)
    val width = spacing * 5
    val height = spacing * 7

    Canvas(modifier = modifier.width(width).height(height)) {
        val stepX = size.width / 5f
        val stepY = size.height / 7f
        val radius = min(stepX, stepY) * 0.38f

        for (row in 0 until 7) {
            for (col in 0 until 5) {
                val isActive = matrix[row][col]
                val center = Offset(col * stepX + stepX / 2f, row * stepY + stepY / 2f)
                if (isActive) {
                    // Soft glow aura
                    drawCircle(color = activeColor.copy(alpha = 0.3f), radius = radius * 1.5f, center = center)
                    // Solid dot
                    drawCircle(color = activeColor, radius = radius, center = center)
                } else {
                    drawCircle(color = inactiveColor, radius = radius * 0.65f, center = center)
                }
            }
        }
    }
}

@Composable
fun MatrixColon(
    spacing: Dp,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    val width = spacing * 2
    val height = spacing * 7

    Canvas(modifier = modifier.width(width).height(height)) {
        val stepX = size.width / 2f
        val stepY = size.height / 7f
        val radius = min(stepX, stepY) * 0.40f

        for (row in 0 until 7) {
            for (col in 0 until 2) {
                val isColonDot = (row == 2 || row == 4) && (col == 1)
                val center = Offset(col * stepX + stepX / 2f, row * stepY + stepY / 2f)
                if (isColonDot) {
                    drawCircle(color = activeColor.copy(alpha = 0.3f), radius = radius * 1.5f, center = center)
                    drawCircle(color = activeColor, radius = radius, center = center)
                }
            }
        }
    }
}

private fun get5x7Matrix(c: Char): Array<BooleanArray> {
    return when (c) {
        '0' -> arrayOf(
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, true, true, true, false)
        )
        '1' -> arrayOf(
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, true, true, false, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, true, true, true, false)
        )
        '2' -> arrayOf(
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(false, false, true, true, false),
            booleanArrayOf(false, true, false, false, false),
            booleanArrayOf(true, false, false, false, false),
            booleanArrayOf(true, true, true, true, true)
        )
        '3' -> arrayOf(
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(false, false, true, true, false),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, true, true, true, false)
        )
        '4' -> arrayOf(
            booleanArrayOf(false, false, false, true, false),
            booleanArrayOf(false, false, true, true, false),
            booleanArrayOf(false, true, false, true, false),
            booleanArrayOf(true, false, false, true, false),
            booleanArrayOf(true, true, true, true, true),
            booleanArrayOf(false, false, false, true, false),
            booleanArrayOf(false, false, false, true, false)
        )
        '5' -> arrayOf(
            booleanArrayOf(true, true, true, true, true),
            booleanArrayOf(true, false, false, false, false),
            booleanArrayOf(true, true, true, true, false),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, true, true, true, false)
        )
        '6' -> arrayOf(
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(true, false, false, false, false),
            booleanArrayOf(true, true, true, true, false),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, true, true, true, false)
        )
        '7' -> arrayOf(
            booleanArrayOf(true, true, true, true, true),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(false, false, false, true, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, true, false, false, false),
            booleanArrayOf(false, true, false, false, false),
            booleanArrayOf(false, true, false, false, false)
        )
        '8' -> arrayOf(
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, true, true, true, false)
        )
        '9' -> arrayOf(
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, true, true, true, true),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, true, true, true, false)
        )
        else -> Array(7) { BooleanArray(5) { false } }
    }
}
