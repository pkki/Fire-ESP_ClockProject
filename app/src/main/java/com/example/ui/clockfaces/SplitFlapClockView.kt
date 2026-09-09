package com.example.ui.clockfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CurrentTimeState
import com.example.model.ClockPreferencesState

@Composable
fun SplitFlapClockView(
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

    val cardBg = if (isNight) Color(0xFF0F0F12) else Color(0xFF16161B)
    val cardBorder = if (isNight) Color(0xFF222228) else Color(0xFF2E2E38)
    val textColor = if (isNight) palette.primary.copy(alpha = 0.6f) else Color(0xFFEAEAEE)
    val accentColor = if (isNight) palette.primary.copy(alpha = 0.4f) else palette.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("split_flap_clock_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Subtle top label
        Text(
            text = "SOLARI STYLE SPLIT-FLAP · ${timeState.timezoneDisplayName.uppercase()}",
            color = Color(0xFF6C6C78),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Hours pair
            SplitFlapCard(text = hStr, cardWidth = 110.dp, cardHeight = 140.dp, cardBg = cardBg, cardBorder = cardBorder, textColor = textColor)

            Spacer(modifier = Modifier.width(14.dp))
            // Colon dots
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.width(8.dp).height(8.dp).background(accentColor, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.width(8.dp).height(8.dp).background(accentColor, RoundedCornerShape(2.dp)))
            }
            Spacer(modifier = Modifier.width(14.dp))

            // Minutes pair
            SplitFlapCard(text = mStr, cardWidth = 110.dp, cardHeight = 140.dp, cardBg = cardBg, cardBorder = cardBorder, textColor = textColor)

            if (preferences.showSeconds) {
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.width(6.dp).height(6.dp).background(accentColor.copy(alpha = 0.6f), RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.width(6.dp).height(6.dp).background(accentColor.copy(alpha = 0.6f), RoundedCornerShape(2.dp)))
                }
                Spacer(modifier = Modifier.width(14.dp))

                // Seconds pair (slightly smaller)
                SplitFlapCard(text = sStr, cardWidth = 84.dp, cardHeight = 110.dp, cardBg = cardBg, cardBorder = cardBorder, textColor = accentColor)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Date readout
        Text(
            text = "${timeState.formattedDateFullJa} · ${timeState.dayOfWeekEn}",
            color = Color(0xFF888896),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun SplitFlapCard(
    text: String,
    cardWidth: Dp,
    cardHeight: Dp,
    cardBg: Color,
    cardBorder: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Card content: Text
        Text(
            text = text,
            color = textColor,
            fontSize = (cardHeight.value * 0.58f).sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = (-2).sp
        )

        // Upper Card subtle highlight gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight / 2)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
                    )
                )
        )

        // Lower Card subtle shadow gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight / 2)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.1f))
                    )
                )
        )

        // Center split seam line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.Black.copy(alpha = 0.85f))
        )

        // Side mechanical hinge notches
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(8.dp)
                .align(Alignment.CenterStart)
                .background(Color(0xFF0A0A0C), RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
        )
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(8.dp)
                .align(Alignment.CenterEnd)
                .background(Color(0xFF0A0A0C), RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp))
        )
    }
}
