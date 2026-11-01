package com.iwankurnia.hub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaults = Typography()

/**
 * Hub typography: the system font (Google Sans / Roboto Flex on most Android 16 phones). The
 * compact 20sp "Hub" title is [Typography.titleLarge]; [Typography.displaySmall] stays for the
 * onboarding heading.
 */
val HubTypography = Typography(
    displaySmall = defaults.displaySmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = defaults.titleLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = defaults.titleMedium.copy(fontFamily = FontFamily.Default),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = FontFamily.Default),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = FontFamily.Default),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
    ),
)
