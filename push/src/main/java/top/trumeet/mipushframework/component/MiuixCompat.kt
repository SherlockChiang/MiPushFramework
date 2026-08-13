package top.trumeet.mipushframework.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtil.Companion.dismissDialog
import top.yukonga.miuix.kmp.utils.SmoothRoundedCornerShape

/**
 * Small page-level adapters for the Miuix API.
 *
 * Miuix intentionally exposes a compact API (for example, [Button] takes a string instead of a
 * slot).  Keeping these adapters local to the app lets the existing pages retain their action
 * slots while ensuring that the rendered controls are still genuine Miuix controls.
 */
@Composable
fun MiuixPageScaffold(
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = bottomBar,
        containerColor = MiuixTheme.colorScheme.background,
        content = content,
    )
}

@Composable
fun MiuixActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    // Surface is the slot-capable primitive used by Miuix Button internally.  The transparent
    // variant is appropriate for dialog/text actions and keeps the original action semantics.
    Surface(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        shape = SmoothRoundedCornerShape(16.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = 58.dp, minHeight = 40.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun MiuixActionIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        backgroundColor = Color.Transparent,
        content = content,
    )
}

@Composable
fun MiuixDialog(
    title: String,
    show: MutableState<Boolean>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SuperDialog(
        modifier = modifier,
        title = title,
        show = show,
        onDismissRequest = {
            dismissDialog(show)
            onDismiss()
        },
        content = content,
    )
}

@Composable
fun MiuixInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = false,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        enabled = enabled,
        singleLine = singleLine,
    )
}

@Composable
fun MiuixBottomNavigation(
    items: List<NavigationItem>,
    selected: Int,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        items = items,
        selected = selected,
        onClick = onClick,
        defaultWindowInsetsPadding = true,
    )
}
