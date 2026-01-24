package com.aayush.simpleai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import simpleai.composeapp.generated.resources.EBGaramond_Italic_VariableFont_wght
import simpleai.composeapp.generated.resources.EBGaramond_VariableFont_wght
import simpleai.composeapp.generated.resources.Res

// Default Material 3 typography values


@Composable
fun getTypography(): Typography {

    val baseline = Typography()

    val garamond = FontFamily(
        Font(
            Res.font.EBGaramond_VariableFont_wght,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(value = FontWeight.Normal.weight)
            )
        ),
        Font(
            Res.font.EBGaramond_Italic_VariableFont_wght,
            style = FontStyle.Italic,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(value = FontWeight.Normal.weight)
            )
        ),
    )

    return Typography(
        displayLarge = baseline.displayLarge.copy(fontFamily = garamond),
        displayMedium = baseline.displayMedium.copy(fontFamily = garamond),
        displaySmall = baseline.displaySmall.copy(fontFamily = garamond),
        headlineLarge = baseline.headlineLarge.copy(fontFamily = garamond),
        headlineMedium = baseline.headlineMedium.copy(fontFamily = garamond),
        headlineSmall = baseline.headlineSmall.copy(fontFamily = garamond),
        titleLarge = baseline.titleLarge.copy(fontFamily = garamond),
        titleMedium = baseline.titleMedium.copy(fontFamily = garamond),
        titleSmall = baseline.titleSmall.copy(fontFamily = garamond),
        bodyLarge = baseline.bodyLarge,
        bodyMedium = baseline.bodyMedium,
        bodySmall = baseline.bodySmall,
        labelLarge = baseline.labelLarge,
        labelMedium = baseline.labelMedium,
        labelSmall = baseline.labelSmall,
    )
}

