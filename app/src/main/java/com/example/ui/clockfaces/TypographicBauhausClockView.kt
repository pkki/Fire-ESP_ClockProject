package com.example.ui.clockfaces

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CurrentTimeState
import com.example.model.ClockPreferencesState

/**
 * Modern Bauhaus / Architectural Typographic Clock Face.
 * Massive geometric numerals, striking typography, hairline seconds progression.
 */
@Composable
fun TypographicBauhausClockView(
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

    val primaryTextColor = if (isNight) palette.primary.copy(alpha = 0.5f) else Color(0xFFF6F6F9)
    val accentColor = if (isNight) palette.primary.copy(alpha = 0.4f) else palette.primary
    val subTextColor = if (isNight) Color(0xFF444448) else Color(0xFF7E7E8A)

    val secondsProgress = (timeState.second * 1000 + timeState.millisecond) / 60000f
    val animatedProgress by animateFloatAsState(targetValue = secondsProgress, label = "sec_prog")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("typographic_bauhaus_clock_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Date & Day ribbon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${timeState.dayOfWeekEn} · ${timeState.dayOfWeekJa}",
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )

            Text(
                text = timeState.formattedDateFullEn.uppercase(),
                color = subTextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big numerals display
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = hStr,
                color = primaryTextColor,
                fontSize = 120.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-4).sp,
                lineHeight = 110.sp
            )

            Text(
                text = ":",
                color = accentColor.copy(alpha = 0.8f),
                fontSize = 100.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
            )

            Text(
                text = mStr,
                color = primaryTextColor,
                fontSize = 120.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-4).sp,
                lineHeight = 110.sp
            )

            if (preferences.showSeconds) {
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "SEC",
                        color = subTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = sStr,
                        color = accentColor,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Continuous precision hairline progress bar for current minute
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(2.dp)
                .background(Color(0xFF1B1B20))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accentColor.copy(alpha = 0.3f), accentColor)
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = timeState.timezoneDisplayName,
            color = subTextColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 2.sp
        )
    }
}
