package top.trumeet.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.defaultTextStyles

internal val AppTextStyles = defaultTextStyles(
    main = TextStyle(fontSize = 17.sp, lineHeight = 1.2f.em),
    paragraph = TextStyle(fontSize = 17.sp, lineHeight = 1.35f.em),
    body1 = TextStyle(fontSize = 16.sp, lineHeight = 1.25f.em),
    body2 = TextStyle(fontSize = 14.sp, lineHeight = 1.25f.em),
    button = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    footnote1 = TextStyle(fontSize = 13.sp, lineHeight = 1.25f.em),
    footnote2 = TextStyle(fontSize = 11.sp, lineHeight = 1.2f.em),
    headline1 = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    headline2 = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    subtitle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    title1 = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
    title2 = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    title4 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
)
