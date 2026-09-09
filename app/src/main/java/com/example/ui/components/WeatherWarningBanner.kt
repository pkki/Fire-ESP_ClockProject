package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WarningSeverity
import com.example.model.WeatherWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherWarningBanner(
    warnings: List<WeatherWarning>,
    regionName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (warnings.isEmpty()) return

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clickable(onClick = onClick)
                .testTag("weather_warning_banner")
        ) {
            warnings.forEach { warning ->
                WarningPill(warning = warning)
            }
        }
    }
}

@Composable
fun WarningPill(
    warning: WeatherWarning,
    modifier: Modifier = Modifier
) {
    // Exact colors matching the official JMA advisory badge in user's image
    val (bgColor, textColor) = when (warning.severity) {
        WarningSeverity.ADVISORY -> Pair(Color(0xFFF7DF1E), Color(0xFF1C1C22)) // Yellow advisory pill
        WarningSeverity.WARNING -> Pair(Color(0xFFE52213), Color(0xFFFFFFFF))  // Red warning pill
        WarningSeverity.SPECIAL_WARNING -> Pair(Color(0xFF880E4F), Color(0xFFFFFFFF)) // Purple special warning pill
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = warning.title,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
