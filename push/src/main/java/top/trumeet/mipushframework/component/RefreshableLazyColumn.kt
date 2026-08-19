package top.trumeet.mipushframework.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.xiaomi.xmsf.R
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh

@Composable
fun RefreshableLazyColumn(
    doRefresh: (onRefreshed: () -> Unit) -> Unit,
    isNeedMore: (lastVisibleIndex: Int) -> Boolean,
    doLoadMore: (onRefreshed: () -> Unit) -> Unit,
    isNeedRefresh: Boolean = false,
    modifier: Modifier = Modifier,
    bottomContentPadding: PaddingValues = PaddingValues(bottom = 112.dp),
    content: LazyListScope.() -> Unit
) {
    val currentIsNeedMore by rememberUpdatedState(isNeedMore)
    val currentDoLoadMore by rememberUpdatedState(doLoadMore)
    val currentDoRefresh by rememberUpdatedState(doRefresh)

    var isLoading by remember { mutableStateOf(false) }
    val currentIsLoading by rememberUpdatedState(isLoading)
    val requestLock = remember { Mutex() }
    val lazyListState = rememberLazyListState()

    // Both pull-to-refresh and the end-of-list observer enter through this gate.  A Mutex is
    // intentionally held until the caller invokes onRefreshed, so a slow database/network load
    // cannot start a second request while the first one is still in flight.
    fun request(work: ((() -> Unit) -> Unit)) {
        if (!requestLock.tryLock()) return
        isLoading = true
        var completed = false
        val complete = {
            if (!completed) {
                completed = true
                isLoading = false
                requestLock.unlock()
            }
        }
        try {
            work(complete)
        } catch (t: Throwable) {
            complete()
            throw t
        }
    }

    // Initial loads are launched from an effect keyed by the flag.  This avoids SideEffect
    // repeatedly dispatching refreshes on every recomposition (which was the source of the
    // occasional refresh storm).
    LaunchedEffect(isNeedRefresh) {
        if (isNeedRefresh) request(currentDoRefresh)
    }

    SwipeRefresh(
        state = rememberSwipeRefreshState(isLoading),
        onRefresh = {
            request(currentDoRefresh)
        }
    ) {
        LaunchedEffect(lazyListState) {
            snapshotFlow {
                lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            }.collect { lastIndex ->
                if (!currentIsLoading && currentIsNeedMore(lastIndex)) {
                    request(currentDoLoadMore)
                }
            }
        }
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = bottomContentPadding,
                content = content,
            )
            RefreshFloatingActions(
                modifier = Modifier.align(Alignment.BottomEnd),
                listState = lazyListState,
                loading = isLoading,
                onRefresh = { request(currentDoRefresh) },
            )
        }
    }
}

@Composable
private fun RefreshFloatingActions(
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val canGoTop = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 0
    Column(
        modifier = modifier
            .padding(end = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        MiuixCircularAction(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = stringResource(R.string.action_back_to_top),
            enabled = canGoTop,
            onClick = {
                // The button only becomes enabled after scrolling, so this animation is cheap and
                // does not allocate a second list state.
                scope.launch { listState.animateScrollToItem(0) }
            },
        )
        MiuixCircularAction(
            icon = Icons.Default.Refresh,
            contentDescription = stringResource(R.string.action_refresh),
            enabled = !loading,
            onClick = onRefresh,
        )
    }
}

@Composable
private fun MiuixCircularAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(48.dp),
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3f,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) MiuixTheme.colorScheme.onPrimary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
