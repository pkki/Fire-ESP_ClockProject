package com.example.ui.clockfaces

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CurrentTimeState
import com.example.model.ClockPreferencesState
import kotlin.math.min

/**
 * Studio Bold Modern Clock Face:
 * Fills the screen with ultra-large, beautifully weighted modern numerals.
 * Specially optimized for 1024x600 widescreen desk displays.
 */
@Composable
fun StudioBoldClockView(
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

    val numColor = if (isNight) palette.primary.copy(alpha = 0.6f) else Color(0xFFF2F2F7)
    val accentColor = if (isNight) palette.primary.copy(alpha = 0.5f) else palette.primary
    val dimColor = if (isNight) Color(0xFF33333C) else Color(0xFF636370)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag("studio_bold_clock_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Prominent Date Ribbon
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x33181824))
                .border(1.2.dp, Color(0x35FFFFFF), RoundedCornerShape(18.dp))
                .padding(horizontal = 26.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = timeState.formattedDateFullJa,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = timeState.timezoneDisplayName.uppercase(),
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Giant Edge-to-Edge Numerals
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            val showSec = preferences.showSeconds
            val fontSizeVal: Float = if (showSec) {
                min(maxWidth.value / 4.4f, maxHeight.value * 0.72f)
            } else {
                min(maxWidth.value / 3.1f, maxHeight.value * 0.85f)
            }
            val fontSizeSp = fontSizeVal.sp

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Hours
                Text(
                    text = hStr,
                    color = numColor,
                    fontSize = fontSizeSp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-4).sp
                )

                // Colon
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy((fontSizeVal * 0.16f).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size((fontSizeVal * 0.08f).dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Box(
                        modifier = Modifier
                            .size((fontSizeVal * 0.08f).dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }

                // Minutes
                Text(
                    text = mStr,
                    color = numColor,
                    fontSize = fontSizeSp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-4).sp
                )

                // Seconds
                if (showSec) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = sStr,
                            color = accentColor,
                            fontSize = (fontSizeVal * 0.46f).sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-1).sp
                        )
                        if (!preferences.is24Hour) {
                            Text(
                                text = if (timeState.isPm) "PM" else "AM",
                                color = dimColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }
        }

        if (!preferences.showSeconds && !preferences.is24Hour) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (timeState.isPm) "PM" else "AM",
                color = accentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
        }
    }
}
