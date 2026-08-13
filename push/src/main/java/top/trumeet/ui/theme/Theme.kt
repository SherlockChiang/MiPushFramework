package top.trumeet.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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

@Composable
fun Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12+ supplies its system accent while Miuix keeps MIUI surface tokens.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicPrimaryArgb = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(
            ContextCompat.getColor(
                context,
                if (darkTheme) android.R.color.system_accent1_200
                else android.R.color.system_accent1_600
            )
        ).value
    } else {
        null
    }
    val baseColors = if (darkTheme) DarkColorScheme else LightColorScheme
    val colors = remember(darkTheme, dynamicPrimaryArgb) {
        dynamicPrimaryArgb?.let { argb ->
            val primary = Color(argb)
            baseColors.copy(
                primary = primary,
                primaryVariant = primary,
                onTertiaryContainer = primary,
            )
        } ?: baseColors
    }

    MiuixTheme(
        colors = colors,
        textStyles = AppTextStyles,
        content = content,
    )
}
