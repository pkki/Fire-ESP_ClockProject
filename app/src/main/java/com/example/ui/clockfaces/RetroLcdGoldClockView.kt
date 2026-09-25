package com.example.ui.clockfaces

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CurrentTimeState
import com.example.model.ClockPreferencesState

/**
 * Retro Classic LCD Gold Clock Face.
 * Faithfully reproduces the user's reference image (Image 3):
 * - Authentic vintage gold/olive-yellow LCD display panel
 * - Dark slate LCD segment pigment
 * - Realistic unlit ghost segments
 * - AM/PM indicator on top left
 * - HOUR, MINUTE, SECOND labels positioned beneath each digit group
 */
@Composable
fun RetroLcdGoldClockView(
    timeState: CurrentTimeState,
    preferences: ClockPreferencesState,
    modifier: Modifier = Modifier
) {
    val isNight = preferences.isNightMode

    // Signature colors from Image 3:
    // Active: Dark slate LCD pigment (#2B2E34)
    // Ghost: Faint olive-yellow unlit LCD trace (#D1CF68)
    // Label: Dark slate (#2B2E34)
    val activeColor = if (isNight) Color(0xFF1E2024).copy(alpha = 0.5f) else Color(0xFF2B2E34)
    val ghostColor = if (isNight) Color(0xFF555422).copy(alpha = 0.3f) else Color(0xFFD1CF68)
    val labelColor = if (isNight) Color(0xFF2B2E34).copy(alpha = 0.5f) else Color(0xFF2B2E34)

    val hourVal = if (preferences.is24Hour) timeState.hour24 else timeState.hour12
    val hStr = hourVal.toString().padStart(2, '0')
    val mStr = timeState.minute.toString().padStart(2, '0')
    val sStr = timeState.second.toString().padStart(2, '0')

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("retro_lcd_gold_view"),
        contentAlignment = Alignment.Center
    ) {
        val digitW = 66.dp
        val digitH = 146.dp
        val colonW = 20.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AM / PM Indicator (Left side top aligned)
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .height(digitH),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = if (timeState.isPm) "PM" else "AM",
                    color = activeColor,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp
                )
            }

            // HOUR Group
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
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "HOUR",
                    color = labelColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))
            ColonSeparator(
                width = colonW,
                height = digitH,
                activeColor = activeColor,
                ghostColor = ghostColor
            )
            Spacer(modifier = Modifier.width(10.dp))

            // MINUTE Group
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
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "MINUTE",
                    color = labelColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            }

            // SECOND Group (if enabled)
            if (preferences.showSeconds) {
                Spacer(modifier = Modifier.width(22.dp))
                val sDigitW = digitW * 0.65f
                val sDigitH = digitH * 0.65f

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 36.dp)
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
                    Spacer(modifier = Modifier.height(10.dp))
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
}
