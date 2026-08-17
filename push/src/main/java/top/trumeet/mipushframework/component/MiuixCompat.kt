package top.trumeet.mipushframework.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
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
    floating: Boolean = true,
) {
    if (floating) {
        require(items.size in 2..5) { "BottomBar must have between 2 and 5 items" }
        val tabWidth = 76.dp
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selected.coerceIn(items.indices),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "MiuixBottomNavigationIndicator",
        )

        // Miuix 0.2.x predates FloatingNavigationBar and the blur module used by current
        // KernelSU. This is its deliberate non-blur compatibility path: the same intrinsic-width
        // 64dp pill, equal 76dp tabs and one animated 56dp selection indicator.
        Surface(
            modifier = modifier.wrapContentWidth(),
            shape = SmoothRoundedCornerShape(32.dp),
            color = MiuixTheme.colorScheme.surfaceContainer,
            shadowElevation = 1f,
        ) {
            Box(
                modifier = Modifier
                    .height(64.dp)
                    .width(tabWidth * items.size + 8.dp)
                    .padding(4.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .height(56.dp),
                    shape = SmoothRoundedCornerShape(28.dp),
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
                ) {}

                Row(modifier = Modifier.fillMaxSize()) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selected
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantActions
                            },
                            label = "MiuixBottomNavigationContent",
                        )
                        Surface(
                            onClick = { onClick(index) },
                            modifier = Modifier
                                .width(tabWidth)
                                .height(56.dp)
                                .semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    this.selected = isSelected
                                },
                            shape = SmoothRoundedCornerShape(28.dp),
                            color = Color.Transparent,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(
                                    1.dp,
                                    Alignment.CenterVertically,
                                ),
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = contentColor,
                                )
                                Text(
                                    text = item.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    color = contentColor,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        NavigationBar(
            modifier = modifier,
            items = items,
            selected = selected,
            onClick = onClick,
            defaultWindowInsetsPadding = true,
        )
    }
}
