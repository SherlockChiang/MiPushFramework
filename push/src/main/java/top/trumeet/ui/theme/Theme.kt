package top.trumeet.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme as materialDarkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme as materialLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private val DarkColorScheme: Colors = darkColorScheme(
    primary = MiuixBlueDark,
    primaryVariant = MiuixBlueDark,
    onTertiaryContainer = MiuixBlueDark,
)

private val LightColorScheme: Colors = lightColorScheme(
    primary = MiuixBlueLight,
    primaryVariant = MiuixBlueLight,
    onTertiaryContainer = MiuixBlueLight,
)

fun materialToMiuixColors(
    materialColors: ColorScheme,
    baseColors: Colors
): Colors {
    return baseColors.copy(
        primary = materialColors.primary,
        primaryVariant = materialColors.primary,
        onPrimary = materialColors.onPrimary,
        primaryContainer = materialColors.primaryContainer,
        onPrimaryContainer = materialColors.onPrimaryContainer,
        secondary = materialColors.secondary,
        onSecondary = materialColors.onSecondary,
        secondaryContainer = materialColors.secondaryContainer,
        onSecondaryContainer = materialColors.onSecondaryContainer,
        background = materialColors.background,
        onBackground = materialColors.onBackground,
        surface = materialColors.surfaceContainerLow,
        onSurface = materialColors.onSurface,
        surfaceVariant = materialColors.surfaceVariant,
        surfaceContainer = materialColors.surfaceContainer,
        onSurfaceContainer = materialColors.onSurface,
        surfaceContainerHigh = materialColors.surfaceContainerHigh,
        onSurfaceContainerHigh = materialColors.onSurface,
        surfaceContainerHighest = materialColors.surfaceContainerHighest,
        onSurfaceContainerHighest = materialColors.onSurface,
        onSurfaceSecondary = materialColors.onSurfaceVariant,
        onSurfaceVariantSummary = materialColors.onSurfaceVariant,
        onSurfaceVariantActions = materialColors.onSurfaceVariant,
        outline = materialColors.outline,
        dividerLine = materialColors.outlineVariant,
        onTertiaryContainer = materialColors.onTertiaryContainer,
    )
}

@Composable
fun Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12+ supplies its system accent while Miuix keeps MIUI surface tokens.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val materialColors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) {
            materialDarkColorScheme(primary = MiuixBlueDark)
        } else {
            materialLightColorScheme(primary = MiuixBlueLight)
        }
    }
    val configuration = LocalConfiguration.current
    val baseColors = if (darkTheme) DarkColorScheme else LightColorScheme
    val colors = remember(context, configuration, darkTheme, dynamicColor, materialColors) {
        materialToMiuixColors(materialColors, baseColors)
    }

    MaterialTheme(colorScheme = materialColors) {
        MiuixTheme(
            colors = colors,
            textStyles = AppTextStyles,
            content = content,
        )
    }
}
