package com.example.ui.clockfaces

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
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
import com.example.model.ColorPalette
import kotlin.math.min

/**
 * 7-Segment Digital Clock matching the user's uploaded reference image.
 * Features realistic beveled segment geometry, inactive segment ghosts, glowing amber/neon,
 * and elegant metadata header.
 */
@Composable
fun SevenSegmentClockView(
    timeState: CurrentTimeState,
    preferences: ClockPreferencesState,
    modifier: Modifier = Modifier
) {
    val palette = preferences.colorPalette
    val isNight = preferences.isNightMode

    // Subtle colon pulse
    val infiniteTransition = rememberInfiniteTransition(label = "colon_pulse")
    val colonAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colon_alpha"
    )

    val hourVal = if (preferences.is24Hour) timeState.hour24 else timeState.hour12
    val hStr = hourVal.toString().padStart(2, '0')
    val mStr = timeState.minute.toString().padStart(2, '0')
    val sStr = timeState.second.toString().padStart(2, '0')

    val activeColor = if (isNight) palette.primary.copy(alpha = 0.45f) else palette.primary
    val ghostColor = if (isNight) palette.inactiveSegment.copy(alpha = 0.3f) else palette.inactiveSegment

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .testTag("seven_segment_clock_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Top Header matching user's photo: "Japan Standard Time (JST)"
        Text(
            text = timeState.timezoneDisplayName,
            color = activeColor.copy(alpha = if (isNight) 0.35f else 0.75f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Date Display
        Text(
            text = "${timeState.formattedDateFullEn.uppercase()} · ${timeState.dayOfWeekJa}",
            color = activeColor.copy(alpha = if (isNight) 0.25f else 0.50f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Main 7-Segment Display Canvas area
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            val availableWidth = maxWidth
            val availableHeight = maxHeight

            // Adaptive sizing based on whether seconds are shown
            val digitCount = if (preferences.showSeconds) 6 else 4
            val colonCount = if (preferences.showSeconds) 2 else 1

            // Calculate optimal digit width and height
            // Digit aspect ratio approx W:H = 1:1.8
            val totalUnits = digitCount * 1.0f + colonCount * 0.35f + (digitCount - 1) * 0.12f
            val digitW = min((availableWidth.value / totalUnits), availableHeight.value / 1.7f).dp
            val digitH = digitW * 1.75f
            val colonW = digitW * 0.32f

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Hours
                SevenSegmentDigit(char = hStr[0], width = digitW, height = digitH, activeColor = activeColor, ghostColor = ghostColor)
                Spacer(modifier = Modifier.width(digitW * 0.12f))
                SevenSegmentDigit(char = hStr[1], width = digitW, height = digitH, activeColor = activeColor, ghostColor = ghostColor)

                // Colon
                ColonSeparator(width = colonW, height = digitH, activeColor = activeColor.copy(alpha = colonAlpha), ghostColor = ghostColor)

                // Minutes
                SevenSegmentDigit(char = mStr[0], width = digitW, height = digitH, activeColor = activeColor, ghostColor = ghostColor)
                Spacer(modifier = Modifier.width(digitW * 0.12f))
                SevenSegmentDigit(char = mStr[1], width = digitW, height = digitH, activeColor = activeColor, ghostColor = ghostColor)

                // Seconds (if enabled)
                if (preferences.showSeconds) {
                    ColonSeparator(width = colonW, height = digitH, activeColor = activeColor.copy(alpha = colonAlpha), ghostColor = ghostColor)
                    SevenSegmentDigit(char = sStr[0], width = digitW, height = digitH, activeColor = activeColor, ghostColor = ghostColor)
                    Spacer(modifier = Modifier.width(digitW * 0.12f))
                    SevenSegmentDigit(char = sStr[1], width = digitW, height = digitH, activeColor = activeColor, ghostColor = ghostColor)
                }
            }
        }

        if (!preferences.is24Hour) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (timeState.isPm) "PM" else "AM",
                color = activeColor.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        }
    }
}

/**
 * Renders a single 7-segment digit with realistic beveled polygons.
 */
@Composable
fun SevenSegmentDigit(
    char: Char,
    width: Dp,
    height: Dp,
    activeColor: Color,
    ghostColor: Color,
    modifier: Modifier = Modifier
) {
    val segments = getSegmentsForChar(char)

    Canvas(modifier = modifier.width(width).height(height)) {
        val w = size.width
        val h = size.height

        val thickness = w * 0.175f // segment thickness
        val halfT = thickness / 2f
        val gap = w * 0.035f // small bevel gap between segments

        val midY = h / 2f

        // Segment A: Top horizontal
        drawBeveledHorizSegment(
            left = thickness * 0.6f + gap,
            right = w - thickness * 0.6f - gap,
            top = 0f,
            thickness = thickness,
            active = segments[0],
            activeColor = activeColor,
            ghostColor = ghostColor
        )

        // Segment B: Top right vertical
        drawBeveledVertSegment(
            top = thickness * 0.6f + gap,
            bottom = midY - halfT * 0.6f - gap,
            right = w,
            thickness = thickness,
            active = segments[1],
            activeColor = activeColor,
            ghostColor = ghostColor
        )

        // Segment C: Bottom right vertical
        drawBeveledVertSegment(
            top = midY + halfT * 0.6f + gap,
            bottom = h - thickness * 0.6f - gap,
            right = w,
            thickness = thickness,
            active = segments[2],
            activeColor = activeColor,
            ghostColor = ghostColor
        )

        // Segment D: Bottom horizontal
        drawBeveledHorizSegment(
            left = thickness * 0.6f + gap,
            right = w - thickness * 0.6f - gap,
            top = h - thickness,
            thickness = thickness,
            active = segments[3],
            activeColor = activeColor,
            ghostColor = ghostColor
        )

        // Segment E: Bottom left vertical
        drawBeveledVertSegment(
            top = midY + halfT * 0.6f + gap,
            bottom = h - thickness * 0.6f - gap,
            right = thickness,
            thickness = thickness,
            active = segments[4],
            activeColor = activeColor,
            ghostColor = ghostColor
        )

        // Segment F: Top left vertical
        drawBeveledVertSegment(
            top = thickness * 0.6f + gap,
            bottom = midY - halfT * 0.6f - gap,
            right = thickness,
            thickness = thickness,
            active = segments[5],
            activeColor = activeColor,
            ghostColor = ghostColor
        )

        // Segment G: Middle horizontal
        drawBeveledHorizSegment(
            left = thickness * 0.6f + gap,
            right = w - thickness * 0.6f - gap,
            top = midY - halfT,
            thickness = thickness,
            active = segments[6],
            activeColor = activeColor,
            ghostColor = ghostColor
        )
    }
}

/**
 * Draw horizontal beveled segment (trapezoid/hexagon)
 */
private fun DrawScope.drawBeveledHorizSegment(
    left: Float,
    right: Float,
    top: Float,
    thickness: Float,
    active: Boolean,
    activeColor: Color,
    ghostColor: Color
) {
    val halfT = thickness / 2f
    val path = Path().apply {
        moveTo(left + halfT, top)
        lineTo(right - halfT, top)
        lineTo(right, top + halfT)
        lineTo(right - halfT, top + thickness)
        lineTo(left + halfT, top + thickness)
        lineTo(left, top + halfT)
        close()
    }

    if (active) {
        // Outer soft glow layer
        drawPath(path, color = activeColor.copy(alpha = 0.35f), style = Stroke(width = thickness * 0.25f))
        // Solid fill
        drawPath(path, color = activeColor, style = Fill)
    } else {
        // Ghost inactive segment like authentic LED / VFD display
        drawPath(path, color = ghostColor, style = Fill)
    }
}

/**
 * Draw vertical beveled segment (trapezoid/hexagon)
 */
private fun DrawScope.drawBeveledVertSegment(
    top: Float,
    bottom: Float,
    right: Float,
    thickness: Float,
    active: Boolean,
    activeColor: Color,
    ghostColor: Color
) {
    val left = right - thickness
    val halfT = thickness / 2f
    val path = Path().apply {
        moveTo(left + halfT, top)
        lineTo(right, top + halfT)
        lineTo(right, bottom - halfT)
        lineTo(left + halfT, bottom)
        lineTo(left, bottom - halfT)
        lineTo(left, top + halfT)
        close()
    }

    if (active) {
        drawPath(path, color = activeColor.copy(alpha = 0.35f), style = Stroke(width = thickness * 0.25f))
        drawPath(path, color = activeColor, style = Fill)
    } else {
        drawPath(path, color = ghostColor, style = Fill)
    }
}

/**
 * Colon separator with 2 square/rounded beveled dots
 */
@Composable
fun ColonSeparator(
    width: Dp,
    height: Dp,
    activeColor: Color,
    ghostColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.width(width).height(height)) {
        val w = size.width
        val h = size.height
        val dotSize = w * 0.55f
        val x = (w - dotSize) / 2f

        val topDotY = h * 0.34f - dotSize / 2f
        val bottomDotY = h * 0.66f - dotSize / 2f

        // Top dot
        drawRect(color = activeColor.copy(alpha = 0.3f), topLeft = Offset(x - 2f, topDotY - 2f), size = Size(dotSize + 4f, dotSize + 4f))
        drawRect(color = activeColor, topLeft = Offset(x, topDotY), size = Size(dotSize, dotSize))

        // Bottom dot
        drawRect(color = activeColor.copy(alpha = 0.3f), topLeft = Offset(x - 2f, bottomDotY - 2f), size = Size(dotSize + 4f, dotSize + 4f))
        drawRect(color = activeColor, topLeft = Offset(x, bottomDotY), size = Size(dotSize, dotSize))
    }
}

/**
 * Returns array of 7 booleans for [A, B, C, D, E, F, G]
 */
private fun getSegmentsForChar(c: Char): BooleanArray {
    return when (c) {
        '0' -> booleanArrayOf(true, true, true, true, true, true, false)
        '1' -> booleanArrayOf(false, true, true, false, false, false, false)
        '2' -> booleanArrayOf(true, true, false, true, true, false, true)
        '3' -> booleanArrayOf(true, true, true, true, false, false, true)
        '4' -> booleanArrayOf(false, true, true, false, false, true, true)
        '5' -> booleanArrayOf(true, false, true, true, false, true, true)
        '6' -> booleanArrayOf(true, false, true, true, true, true, true)
        '7' -> booleanArrayOf(true, true, true, false, false, false, false)
        '8' -> booleanArrayOf(true, true, true, true, true, true, true)
        '9' -> booleanArrayOf(true, true, true, true, false, true, true)
        else -> booleanArrayOf(false, false, false, false, false, false, false)
    }
}
