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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CurrentTimeState
import com.example.model.ClockFace
import com.example.model.ClockPreferencesState
import kotlin.math.min

/**
 * Multi-Panel Digital Station Clock Dashboard.
 * Faithfully matches the user's uploaded reference images:
 * - Image 1: 7-Segment LED Dashboard (Electric Blue)
 * - Image 2: Square LED Matrix Dashboard (Neon Cyan)
 *
 * Layout features:
 * - Top: YEAR, MONTH, DAY with labels, and a 17x7 dot-matrix Day-of-Week panel (e.g. THU).
 * - Middle: AM/PM, HOUR, MINUTE, SECOND with segment/matrix digits and labels.
 * - Bottom: BATTERY label, 10-bar segmented battery gauge, and 7-segment battery percentage.
 */
@Composable
fun DigitalStationClockView(
    timeState: CurrentTimeState,
    preferences: ClockPreferencesState,
    isMatrixTime: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isNight = preferences.isNightMode

    // Signature color schemes matching reference images:
    // Image 1: Blue LED (#3E80F8), Dark charcoal ghost (#191B22)
    // Image 2: Cyan LED (#00FFFF), Dark purple/navy matrix cell (#1B1429)
    val baseActiveColor = if (isMatrixTime) {
        Color(0xFF00FFFF)
    } else {
        Color(0xFF3E80F8)
    }

    val ghostColor = if (isMatrixTime) {
        Color(0xFF1B1429)
    } else {
        Color(0xFF191B22)
    }

    val labelColor = if (isMatrixTime) {
        Color(0xFF88A09E)
    } else {
        Color(0xFF8E929E)
    }

    val activeColor = if (isNight) baseActiveColor.copy(alpha = 0.45f) else baseActiveColor

    // Digits
    val yStr = timeState.year.toString().padStart(4, '0')
    val mthStr = timeState.month.toString().padStart(2, '0')
    val dStr = timeState.day.toString().padStart(2, '0')

    val hourVal = if (preferences.is24Hour) timeState.hour24 else timeState.hour12
    val hStr = hourVal.toString().padStart(2, '0')
    val mStr = timeState.minute.toString().padStart(2, '0')
    val sStr = timeState.second.toString().padStart(2, '0')

    val day3Letter = timeState.dayOfWeekEn.take(3).uppercase()
    val battPct = timeState.batteryPercent.coerceIn(0, 100)
    val activeBars = ((battPct + 5) / 10).coerceIn(0, 10)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag(if (isMatrixTime) "digital_station_matrix_view" else "digital_station_blue_view"),
        contentAlignment = Alignment.Center
    ) {
        val totalH = maxHeight
        val totalW = maxWidth

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ==========================================
            // 1. TOP ROW: YEAR, MONTH, DAY & DAY-OF-WEEK MATRIX
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Year, Month, Day Group
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // YEAR
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            yStr.forEach { c ->
                                SevenSegmentDigit(
                                    char = c,
                                    width = 18.dp,
                                    height = 32.dp,
                                    activeColor = activeColor,
                                    ghostColor = ghostColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "YEAR",
                            color = labelColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    // MONTH
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            mthStr.forEach { c ->
                                SevenSegmentDigit(
                                    char = c,
                                    width = 18.dp,
                                    height = 32.dp,
                                    activeColor = activeColor,
                                    ghostColor = ghostColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "MONTH",
                            color = labelColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    // DAY
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            dStr.forEach { c ->
                                SevenSegmentDigit(
                                    char = c,
                                    width = 18.dp,
                                    height = 32.dp,
                                    activeColor = activeColor,
                                    ghostColor = ghostColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "DAY",
                            color = labelColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Top Right: Day of Week Dot-Matrix Box (THU, FRI, etc.)
                DayOfWeekMatrixBox(
                    text = day3Letter,
                    width = 84.dp,
                    height = 34.dp,
                    activeColor = activeColor,
                    ghostColor = ghostColor
                )
            }

            // ==========================================
            // 2. MIDDLE ROW: TIME DISPLAY (HOUR : MINUTE SECOND)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AM / PM Indicator (Left)
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .height(130.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = if (timeState.isPm) "PM" else "AM",
                        color = activeColor,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 1.sp
                    )
                }

                if (isMatrixTime) {
                    // --- MATRIX TIME DISPLAY (Image 2) ---
                    val mDigitW = 54.dp
                    val mDigitH = 120.dp
                    val mColonW = 20.dp

                    // HOUR
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SquareMatrixDigit(
                                char = hStr[0],
                                width = mDigitW,
                                height = mDigitH,
                                activeColor = activeColor,
                                ghostColor = ghostColor
                            )
                            SquareMatrixDigit(
                                char = hStr[1],
                                width = mDigitW,
                                height = mDigitH,
                                activeColor = activeColor,
                                ghostColor = ghostColor
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "HOUR",
                            color = labelColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    SquareMatrixColon(
                        width = mColonW,
                        height = mDigitH,
                        activeColor = activeColor,
                        ghostColor = ghostColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    // MINUTE
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SquareMatrixDigit(
                                char = mStr[0],
                                width = mDigitW,
                                height = mDigitH,
                                activeColor = activeColor,
                                ghostColor = ghostColor
                            )
                            SquareMatrixDigit(
                                char = mStr[1],
                                width = mDigitW,
                                height = mDigitH,
                                activeColor = activeColor,
                                ghostColor = ghostColor
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "MINUTE",
                            color = labelColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                    }

                    // SECOND (if enabled)
                    if (preferences.showSeconds) {
                        Spacer(modifier = Modifier.width(20.dp))
                        val sDigitW = mDigitW * 0.65f
                        val sDigitH = mDigitH * 0.65f

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 28.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                SquareMatrixDigit(
                                    char = sStr[0],
                                    width = sDigitW,
                                    height = sDigitH,
                                    activeColor = activeColor,
                                    ghostColor = ghostColor
                                )
                                SquareMatrixDigit(
                                    char = sStr[1],
                                    width = sDigitW,
                                    height = sDigitH,
                                    activeColor = activeColor,
                                    ghostColor = ghostColor
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "SECOND",
                                color = labelColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                } else {
                    // --- 7-SEGMENT TIME DISPLAY (Image 1) ---
                    val digitW = 60.dp
                    val digitH = 132.dp
                    val colonW = 20.dp

                    // HOUR
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SevenSegmentDigit(
                                char = hStr[0],
                                width = digitW,
                                height = digitH,
                                activeColor = activeColor,
                                ghostColor = ghostColor
                            )
                            SevenSegmentDigit(
                                char = hStr[1],
                                width = digitW,
                                height = digitH,
                                activeColor = activeColor,
                                ghostColor = ghostColor
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "HOUR",
                            color = labelColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    ColonSeparator(
                        width = colonW,
                        height = digitH,
                        activeColor = activeColor,
                        ghostColor = ghostColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // MINUTE
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SevenSegmentDigit(
                                char = mStr[0],
                                width = digitW,
                                height = digitH,
                                activeColor = activeColor,
                                ghostColor = ghostColor
                            )
                            SevenSegmentDigit(
                                char = mStr[1],
                                width = digitW,
                                height = digitH,
                                activeColor = activeColor,
                                ghostColor = ghostColor
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "MINUTE",
                            color = labelColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                    }

                    // SECOND (if enabled)
                    if (preferences.showSeconds) {
                        Spacer(modifier = Modifier.width(20.dp))
                        val sDigitW = digitW * 0.65f
                        val sDigitH = digitH * 0.65f

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 34.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SevenSegmentDigit(
                                    char = sStr[0],
                                    width = sDigitW,
                                    height = sDigitH,
                                    activeColor = activeColor,
                                    ghostColor = ghostColor
                                )
                                SevenSegmentDigit(
                                    char = sStr[1],
                                    width = sDigitW,
                                    height = sDigitH,
                                    activeColor = activeColor,
                                    ghostColor = ghostColor
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "SECOND",
                                color = labelColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 3. BOTTOM ROW: BATTERY BAR & PERCENTAGE
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: BATTERY Label
                Text(
                    text = "BATTERY",
                    color = labelColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                // Center: 10 Rounded Battery Bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 10) {
                        val isLit = i < activeBars
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isLit) activeColor else ghostColor)
                        )
                    }
                }

                // Right: 7-Segment Battery Percentage + %
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val battStr = battPct.toString()
                    battStr.forEach { c ->
                        SevenSegmentDigit(
                            char = c,
                            width = 14.dp,
                            height = 24.dp,
                            activeColor = activeColor,
                            ghostColor = ghostColor
                        )
                    }
                    Text(
                        text = " %",
                        color = activeColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * 17x7 Dot-Matrix box displaying a 3-letter Day of Week (e.g. "THU").
 */
@Composable
fun DayOfWeekMatrixBox(
    text: String,
    width: Dp,
    height: Dp,
    activeColor: Color,
    ghostColor: Color,
    modifier: Modifier = Modifier
) {
    val padded = text.take(3).padEnd(3, ' ')
    val m1 = get5x7CharMatrix(padded[0])
    val m2 = get5x7CharMatrix(padded[1])
    val m3 = get5x7CharMatrix(padded[2])

    // 17 columns x 7 rows (5 + 1 space + 5 + 1 space + 5)
    Canvas(
        modifier = modifier
            .width(width)
            .height(height)
            .background(Color(0xFF0C0E14), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF1E222D), RoundedCornerShape(4.dp))
            .padding(3.dp)
    ) {
        val cols = 17
        val rows = 7
        val cellGap = 1.5f
        val cellW = (size.width - cellGap * (cols - 1)) / cols
        val cellH = (size.height - cellGap * (rows - 1)) / rows
        val corner = CornerRadius(cellW * 0.25f, cellH * 0.25f)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val isLit = when {
                    c in 0..4 -> m1[r][c]
                    c == 5 -> false
                    c in 6..10 -> m2[r][c - 6]
                    c == 11 -> false
                    c in 12..16 -> m3[r][c - 12]
                    else -> false
                }

                val x = c * (cellW + cellGap)
                val y = r * (cellH + cellGap)

                if (isLit) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(x, y),
                        size = Size(cellW, cellH),
                        cornerRadius = corner
                    )
                } else {
                    drawRoundRect(
                        color = ghostColor,
                        topLeft = Offset(x, y),
                        size = Size(cellW, cellH),
                        cornerRadius = corner
                    )
                }
            }
        }
    }
}

/**
 * 5x7 Square LED Matrix Digit with realistic grid cells and rounded corners.
 */
@Composable
fun SquareMatrixDigit(
    char: Char,
    width: Dp,
    height: Dp,
    activeColor: Color,
    ghostColor: Color,
    modifier: Modifier = Modifier
) {
    val matrix = get5x7CharMatrix(char)

    Canvas(modifier = modifier.width(width).height(height)) {
        val cols = 5
        val rows = 7
        val gap = size.width * 0.05f
        val cellW = (size.width - gap * (cols - 1)) / cols
        val cellH = (size.height - gap * (rows - 1)) / rows
        val corner = CornerRadius(cellW * 0.2f, cellH * 0.2f)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val isLit = matrix[r][c]
                val x = c * (cellW + gap)
                val y = r * (cellH + gap)

                if (isLit) {
                    // Soft aura
                    drawRoundRect(
                        color = activeColor.copy(alpha = 0.35f),
                        topLeft = Offset(x - 1.5f, y - 1.5f),
                        size = Size(cellW + 3f, cellH + 3f),
                        cornerRadius = corner
                    )
                    // Bright block
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(x, y),
                        size = Size(cellW, cellH),
                        cornerRadius = corner
                    )
                } else {
                    // Inactive unlit cell
                    drawRoundRect(
                        color = ghostColor,
                        topLeft = Offset(x, y),
                        size = Size(cellW, cellH),
                        cornerRadius = corner
                    )
                }
            }
        }
    }
}

/**
 * Colon for Square LED Matrix (2 vertical square dots).
 */
@Composable
fun SquareMatrixColon(
    width: Dp,
    height: Dp,
    activeColor: Color,
    ghostColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.width(width).height(height)) {
        val cols = 2
        val rows = 7
        val gap = size.width * 0.08f
        val cellW = (size.width - gap * (cols - 1)) / cols
        val cellH = (size.height - gap * (rows - 1)) / rows
        val corner = CornerRadius(cellW * 0.2f, cellH * 0.2f)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val isLit = (r == 2 || r == 4) && (c == 0 || c == 1)
                val x = c * (cellW + gap)
                val y = r * (cellH + gap)

                if (isLit) {
                    drawRoundRect(
                        color = activeColor.copy(alpha = 0.35f),
                        topLeft = Offset(x - 1.5f, y - 1.5f),
                        size = Size(cellW + 3f, cellH + 3f),
                        cornerRadius = corner
                    )
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(x, y),
                        size = Size(cellW, cellH),
                        cornerRadius = corner
                    )
                } else {
                    drawRoundRect(
                        color = ghostColor,
                        topLeft = Offset(x, y),
                        size = Size(cellW, cellH),
                        cornerRadius = corner
                    )
                }
            }
        }
    }
}

/**
 * Returns 5x7 boolean matrix for digits 0-9 and uppercase letters A-Z.
 */
fun get5x7CharMatrix(c: Char): Array<BooleanArray> {
    return when (c.uppercaseChar()) {
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
            booleanArrayOf(true, true, true, true, true)
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
            booleanArrayOf(true, false, false, true, false),
            booleanArrayOf(true, false, false, true, false),
            booleanArrayOf(true, false, false, true, false),
            booleanArrayOf(true, true, true, true, true),
            booleanArrayOf(false, false, false, true, false),
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
        'A' -> arrayOf(
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, true, true, true, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true)
        )
        'D' -> arrayOf(
            booleanArrayOf(true, true, true, false, false),
            booleanArrayOf(true, false, false, true, false),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, true, false),
            booleanArrayOf(true, true, true, false, false)
        )
        'E' -> arrayOf(
            booleanArrayOf(true, true, true, true, true),
            booleanArrayOf(true, false, false, false, false),
            booleanArrayOf(true, true, true, true, false),
            booleanArrayOf(true, false, false, false, false),
            booleanArrayOf(true, false, false, false, false),
            booleanArrayOf(true, false, false, false, false),
            booleanArrayOf(true, true, true, true, true)
        )
        'F' -> arrayOf(
            booleanArrayOf(true, true, true, true, true),
            booleanArrayOf(true, false, false, false, false),
            booleanArrayOf(true, true, true, true, false),
            booleanArrayOf(true, false, false, false, false),
            booleanArrayOf(true, false, false, false, false),
            booleanArrayOf(true, false, false, false, false),
            booleanArrayOf(true, false, false, false, false)
        )
        'H' -> arrayOf(
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, true, true, true, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true)
        )
        'I' -> arrayOf(
            booleanArrayOf(true, true, true, true, true),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(true, true, true, true, true)
        )
        'M' -> arrayOf(
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, true, false, true, true),
            booleanArrayOf(true, false, true, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true)
        )
        'N' -> arrayOf(
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, true, false, false, true),
            booleanArrayOf(true, false, true, false, true),
            booleanArrayOf(true, false, false, true, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true)
        )
        'O' -> arrayOf(
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, true, true, true, false)
        )
        'R' -> arrayOf(
            booleanArrayOf(true, true, true, true, false),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, true, true, true, false),
            booleanArrayOf(true, false, true, false, false),
            booleanArrayOf(true, false, false, true, false),
            booleanArrayOf(true, false, false, false, true)
        )
        'S' -> arrayOf(
            booleanArrayOf(false, true, true, true, true),
            booleanArrayOf(true, false, false, false, false),
            booleanArrayOf(false, true, true, true, false),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, true, true, true, false)
        )
        'T' -> arrayOf(
            booleanArrayOf(true, true, true, true, true),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, false, true, false, false),
            booleanArrayOf(false, false, true, false, false)
        )
        'U' -> arrayOf(
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(false, true, true, true, false)
        )
        'W' -> arrayOf(
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, false, false, true),
            booleanArrayOf(true, false, true, false, true),
            booleanArrayOf(true, false, true, false, true),
            booleanArrayOf(true, true, false, true, true),
            booleanArrayOf(true, false, false, false, true)
        )
        else -> Array(7) { BooleanArray(5) { false } }
    }
}
