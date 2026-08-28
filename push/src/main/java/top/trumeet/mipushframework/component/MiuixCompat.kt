package top.trumeet.mipushframework.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

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
        val density = LocalDensity.current
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val rubberBandLimitPx = with(density) { 4.dp.toPx() }
        val totalWidthPx = tabWidthPx * items.size + with(density) { 8.dp.toPx() }
        val lastIndex = items.lastIndex.toFloat()
        val selectedIndex = selected.coerceIn(items.indices)
        val indicatorPosition = remember(items.size) {
            Animatable(selectedIndex.toFloat(), visibilityThreshold = 0.001f)
        }
        var isDragging by remember { mutableStateOf(false) }
        var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
        var panelDragOffsetPx by remember { mutableFloatStateOf(0f) }
        var rubberBandOffsetPx by remember { mutableFloatStateOf(0f) }

        val itemInteractionSources = remember(items.size) {
            List(items.size) { MutableInteractionSource() }
        }
        val selectedPressed by itemInteractionSources[selectedIndex].collectIsPressedAsState()
        val pressProgress by animateFloatAsState(
            targetValue = if (isDragging || selectedPressed) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "MiuixBottomNavigationPress",
        )
        val panelOffsetPx by animateFloatAsState(
            targetValue = if (isDragging) rubberBandOffsetPx else 0f,
            animationSpec = spring(
                dampingRatio = 0.5f,
                stiffness = 300f,
            ),
            label = "MiuixBottomNavigationRubberBand",
        )

        LaunchedEffect(selectedIndex) {
            if (!isDragging) {
                indicatorPosition.animateTo(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = 0.001f,
                    ),
                )
            }
        }

        val dragState = rememberDraggableState { deltaPx ->
            val logicalDelta = deltaPx / tabWidthPx * if (isLtr) 1f else -1f
            // KernelSU bases each update on the current bounded target. This lets the indicator
            // leave an edge immediately when the drag reverses, while the panel keeps a small
            // independent rubber-band displacement and springs home on release.
            dragPosition = (dragPosition + logicalDelta).coerceIn(0f, lastIndex)
            panelDragOffsetPx += deltaPx
            val dragFraction = (panelDragOffsetPx / totalWidthPx).coerceIn(-1f, 1f)
            rubberBandOffsetPx = rubberBandLimitPx * dragFraction.sign *
                EaseOut.transform(abs(dragFraction))
        }

        val displayedPosition = if (isDragging) dragPosition else indicatorPosition.value
        val indicatorTranslationPx = displayedPosition * tabWidthPx * if (isLtr) 1f else -1f
        // Keep the pressed indicator inside the outer pill's 4 dp inset at the edge tabs.
        val indicatorScaleX = 1f + 0.10f * pressProgress
        val indicatorScaleY = 1f - 0.04f * pressProgress

        // Interaction and geometry reference: KernelSU Manager's FloatingBottomBar and
        // BottomBarMiuix (GPL-3.0), independently reimplemented here with the Miuix 0.2.x API:
        // https://github.com/tiann/KernelSU/tree/main/manager/app/src/main/java/me/weishu/kernelsu/ui/component
        // This file does not copy KernelSU source, assets, or its blur dependencies.
        // The compatibility path preserves the reference's 64/4/56/76dp geometry, draggable
        // indicator, RTL-aware motion and edge resistance without importing the newer blur stack.
        val panelWidth = tabWidth * items.size + 8.dp
        Surface(
            modifier = modifier
                // A floating panel must own its intrinsic width.  `wrapContentWidth()` preserves
                // a full-width parent constraint, which makes the Surface paint an opaque strip
                // across the whole bottom row when this component is placed in an overlay.
                .requiredWidth(panelWidth)
                .graphicsLayer { translationX = panelOffsetPx },
            shape = SmoothRoundedCornerShape(32.dp),
            color = MiuixTheme.colorScheme.surfaceContainer,
            shadowElevation = 1f,
        ) {
            Box(
                modifier = Modifier
                    .height(64.dp)
                    .width(panelWidth)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Surface(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = indicatorTranslationPx
                            scaleX = indicatorScaleX
                            scaleY = indicatorScaleY
                            transformOrigin = TransformOrigin.Center
                        }
                        .width(tabWidth)
                        .height(56.dp),
                    shape = SmoothRoundedCornerShape(28.dp),
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.15f),
                ) {}

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup(),
                ) {
                    items.forEachIndexed { index, item ->
                        val isSelected = index == selectedIndex
                        // KernelSU's non-blur path keeps tab content onSurface and lets the
                        // translucent primary indicator alone communicate selection.
                        val contentColor = MiuixTheme.colorScheme.onSurface
                        Surface(
                            modifier = Modifier
                                .width(tabWidth)
                                .height(56.dp)
                                .then(
                                    if (isSelected) {
                                        Modifier.draggable(
                                            state = dragState,
                                            orientation = Orientation.Horizontal,
                                            onDragStarted = {
                                                indicatorPosition.stop()
                                                dragPosition = indicatorPosition.value
                                                panelDragOffsetPx = 0f
                                                rubberBandOffsetPx = 0f
                                                isDragging = true
                                            },
                                            onDragStopped = {
                                                val targetIndex = dragPosition
                                                    .roundToInt()
                                                    .coerceIn(items.indices)
                                                indicatorPosition.snapTo(dragPosition)
                                                isDragging = false
                                                panelDragOffsetPx = 0f
                                                rubberBandOffsetPx = 0f
                                                onClick(targetIndex)
                                                indicatorPosition.animateTo(
                                                    targetValue = targetIndex.toFloat(),
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMediumLow,
                                                        visibilityThreshold = 0.001f,
                                                    ),
                                                )
                                            },
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .selectable(
                                    selected = isSelected,
                                    onClick = { onClick(index) },
                                    role = Role.Tab,
                                    interactionSource = itemInteractionSources[index],
                                    indication = null,
                                )
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
