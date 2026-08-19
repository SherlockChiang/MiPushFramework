package top.trumeet.mipushframework.main.subpage

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elvishew.xlog.XLog
import com.xiaomi.xmsf.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.entities.RegisteredApplication
import top.trumeet.mipushframework.component.AppIcon
import top.trumeet.mipushframework.component.RefreshableLazyColumn
import top.trumeet.mipushframework.component.iconCache
import top.trumeet.mipushframework.main.RegistrationStateStyle
import top.trumeet.mipushframework.utils.ParseUtils
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SmoothRoundedCornerShape

data class AppInfoForDisplay(
    val registrationState: Pair<String, Color>,
    val lastReceiveTime: String,
)

private var g_itemsInfo by mutableStateOf(emptyMap<String, AppInfoForDisplay>())
private var g_items by mutableStateOf(ApplicationPageOperation.MiPushApplications())

@Composable
fun ApplicationList(query: String) {
    val context = LocalContext.current
    ApplicationList(query) {
        val miPushApplications =
            ApplicationPageOperation.getMiPushApplicationsThatQueryMatched(query)
        ApplicationPageOperation.updateRegisteredApplicationDb(
            context,
            miPushApplications.res
        )
        miPushApplications
    }
}

@Composable
fun ApplicationList(
    query: String = "",
    getMiPushApplications: () -> ApplicationPageOperation.MiPushApplications
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    if (isPreview) g_items = getMiPushApplications()
    var isNeedRefresh by rememberSaveable(query) { mutableStateOf(true) }

    val refreshScope = rememberCoroutineScope { Dispatchers.IO }
    var selectedFilter by rememberSaveable(query) { mutableStateOf(ApplicationFilter.All) }
    val onRefresh: (onRefreshed: () -> Unit) -> Unit = { onRefreshed ->
        refreshScope.launch {
            try {
                val applications = getMiPushApplications()
                updateInfos(applications, context)
                withContext(Dispatchers.Main) {
                    g_items = applications
                    isNeedRefresh = false
                    onRefreshed()
                }
                applications.res.forEach {
                    iconCache.cache(it.packageName)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onRefreshed() }
            }
        }
    }

    val visibleItems = filterApplicationsForDisplay(g_items.res, selectedFilter)

    Page {
        Column(Modifier.fillMaxSize()) {
            ApplicationFilterRow(
                selected = selectedFilter,
                onSelected = { selectedFilter = it },
            )
            RefreshableLazyColumn(
                doRefresh = onRefresh,
                isNeedMore = { false },
                doLoadMore = onRefresh,
                isNeedRefresh = isNeedRefresh,
                modifier = Modifier.weight(1f),
            ) {
            items(visibleItems, { it.packageName }) {
                ApplicationItem(it)
            }
            item {
                val notUseMiPushCount by remember {
                    derivedStateOf { g_items.totalPkg - g_items.res.size }
                }
                Footer(notUseMiPushCount)
            }
            }
        }
    }
}

@Composable
private fun ApplicationFilterRow(
    selected: ApplicationFilter,
    onSelected: (ApplicationFilter) -> Unit,
) {
    val filters = listOf(
        ApplicationFilter.All to R.string.application_filter_all,
        ApplicationFilter.Registered to R.string.application_filter_registered,
        ApplicationFilter.NotRegistered to R.string.application_filter_not_registered,
        ApplicationFilter.Unregistered to R.string.application_filter_unregistered,
    )
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters.size) { index ->
            val (filter, label) = filters[index]
            Surface(
                modifier = Modifier,
                onClick = { onSelected(filter) },
                shape = SmoothRoundedCornerShape(18.dp),
                color = if (selected == filter) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Text(
                    text = stringResource(label),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (selected == filter) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
    }
}

private fun updateInfos(
    applications: ApplicationPageOperation.MiPushApplications,
    context: Context
) {
    val infoMap = emptyMap<String, AppInfoForDisplay>().toMutableMap()
    applications.res.forEach {
        infoMap[it.packageName] = AppInfoForDisplay(
            registrationState = RegistrationStateStyle.contentOf(it, context),
            lastReceiveTime = if (it.lastReceiveTime.time == 0L) ""
            else context.getString(R.string.last_receive) + ParseUtils.getFriendlyDateString(
                it.lastReceiveTime,
                Utils.getUTC(),
                context
            ),
        )
    }
    g_itemsInfo = infoMap
}

@Composable
private fun Footer(notUseMiPushCount: Int) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_info_outline_black_24dp),
            null,
            tint = Color(0xFF757575),
            modifier = Modifier.padding(10.dp)
        )
        Text(
            ApplicationPageOperation.getNotSupportHint(
                context,
                notUseMiPushCount
            )
        )
    }
}

@Composable
private fun ApplicationItem(item: RegisteredApplication) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    EventListPageUtils.startManagePermissions(
                        context,
                        item.packageName,
                        true
                    )
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(item.packageName, item.appName, Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                AppInfo(item)
                LastReceive(item)
            }
        }
    }
}

@Composable
private fun LastReceive(item: RegisteredApplication) {
    val info = g_itemsInfo[item.packageName] ?: AppInfoForDisplay(
        registrationState = RegistrationStateStyle.contentOf(item, LocalContext.current),
        lastReceiveTime = "",
    )
    Text(
        info.lastReceiveTime,
        style = MiuixTheme.textStyles.body1,
    )
}

@Composable
private fun AppInfo(item: RegisteredApplication) {
    val info = g_itemsInfo[item.packageName] ?: AppInfoForDisplay(
        registrationState = RegistrationStateStyle.contentOf(item, LocalContext.current),
        lastReceiveTime = "",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            item.appName,
            style = MiuixTheme.textStyles.body1,
            color = info.registrationState.second
        )
        Text(
            info.registrationState.first,
            style = MiuixTheme.textStyles.body2,
            color = info.registrationState.second
        )
    }
}


@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
    showSystemUi = true,
)
@Composable
fun ApplicationListPreview() {
    XLog.init()

    ApplicationList {
        val miPushApplications = ApplicationPageOperation.MiPushApplications()
        miPushApplications.res = listOf(
            registeredApplication(
                RegisteredApplication.RegisteredType.NotRegistered,
                "123"
            ),
            registeredApplication(
                RegisteredApplication.RegisteredType.Registered,
                "qwe"
            ),
            registeredApplication(
                RegisteredApplication.RegisteredType.Registered,
                "asd"
            ),
            registeredApplication(
                RegisteredApplication.RegisteredType.Unregistered,
                "zxc"
            ),
            registeredApplication(
                RegisteredApplication.RegisteredType.Unregistered,
                "456",
                false
            ),
        ) + ('a'..'z').map {
            registeredApplication(
                RegisteredApplication.RegisteredType.NotRegistered,
                it.toString()
            )
        }

        miPushApplications
    }
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
    showSystemUi = true,
)
@Composable
fun OneApplicationWithNonMiPushAppPreview() {
    XLog.init()

    ApplicationList {
        val miPushApplications = ApplicationPageOperation.MiPushApplications()
        miPushApplications.res = listOf(
            registeredApplication(
                RegisteredApplication.RegisteredType.NotRegistered,
                "123"
            )
        )
        miPushApplications.totalPkg = 100
        miPushApplications
    }
}

private fun registeredApplication(
    registeredType: Int,
    appName: String,
    existServices: Boolean = true
): RegisteredApplication {
    val registeredApplication =
        RegisteredApplication(
            null,
            appName,
            RegisteredApplication.Type.ASK,
            true,
            registeredType,
            appName
        )
    registeredApplication.existServices = existServices
    return registeredApplication
}
