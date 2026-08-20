package top.trumeet.mipushframework.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.utils.ConfigCenter
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtil.Companion.dismissDialog

@Composable
fun SettingsItem(
    title: String,
    summary: String? = null,
    content: (@Composable RowScope.() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = DpSize(16.dp, 14.dp),
        title = title,
        summary = summary,
        rightActions = { content?.invoke(this) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun SettingsItem(
    title: String,
    summary: String,
    key: String,
    values: Array<String>,
    defaultValue: String
) {
    SettingsItem(
        title = title,
        summary = summary,
        confirmButton = {},
        content = { dismiss -> ItemLists(key, defaultValue, values, dismiss) },
    )
}

@Composable
fun SettingsItem(
    title: String,
    summary: String,
    confirmButton: @Composable (dismiss: () -> Unit) -> Unit,
    onDismiss: (() -> Unit)? = null,
    content: @Composable (dismiss: () -> Unit) -> Unit
) {
    var shouldShowDialog by remember { mutableStateOf(false) }
    val hideDialog = {
        shouldShowDialog = false
        onDismiss?.invoke()
        Unit
    }

    SettingsItem(
        title = title,
        summary = summary,
        content = {
            SettingsDialog(
                title = title,
                shouldShowDialog = shouldShowDialog,
                onDismiss = hideDialog,
                confirmButton = { confirmButton(hideDialog) },
                content = { content(hideDialog) },
            )
        },
        onClick = { shouldShowDialog = true },
    )
}

@Composable
fun SettingsDialog(
    title: String,
    shouldShowDialog: Boolean,
    onDismiss: () -> Unit,
    confirmButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val show = remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(shouldShowDialog) {
        if (shouldShowDialog) {
            show.value = true
        } else {
            dismissDialog(show)
        }
    }

    SuperDialog(
        title = title,
        show = show,
        onDismissRequest = {
            dismissDialog(show)
            dismiss()
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                confirmButton()
            }
        }
    }
}

@Composable
private fun ItemLists(
    key: String,
    defaultValue: String,
    values: Array<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val preferences = ConfigCenter.getSharedPreferences(context)
    val selected = preferences.getString(key, defaultValue)?.toIntOrNull() ?: 0

    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
        itemsIndexed(values) { index, item ->
            Row(
                modifier = Modifier
                    .clickable {
                        preferences.edit().putString(key, index.toString()).apply()
                        onDismiss()
                    }
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = index == selected,
                    onCheckedChange = null,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = item, style = MiuixTheme.textStyles.body1)
            }
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        SmallTitle(
            text = title,
            insideMargin = DpSize(16.dp, 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    summary: String? = null,
    key: String,
    defaultValue: Boolean,
    enabled: Boolean = true,
    onClick: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val preferences = ConfigCenter.getSharedPreferences(context)
    var checked by remember(key) { mutableStateOf(preferences.getBoolean(key, defaultValue)) }
    SettingsItem(
        title = title,
        summary = summary,
        checked = checked,
        enabled = enabled,
    ) {
        checked = !checked
        preferences.edit().putBoolean(key, checked).apply()
        onClick?.invoke(checked)
    }
}

@Composable
fun SettingsItem(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    checked: Boolean,
    onClick: () -> Unit
) {
    SettingsItem(
        title = title,
        summary = summary,
        content = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun ItemInfo(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 10.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.headline1,
            color = MiuixTheme.colorScheme.onSurface,
        )
        summary?.let {
            Text(
                text = it,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun InfoDialogPreview() {
    SettingsDialog(
        title = stringResource(R.string.pref_title_access_mode),
        shouldShowDialog = true,
        onDismiss = {},
        confirmButton = {},
    ) {
        ItemLists(
            key = "AccessMode",
            defaultValue = "0",
            values = stringArrayResource(R.array.pref_title_access_mode_list_titles),
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsItemPreview() {
    SettingsItem(
        title = stringResource(R.string.settings_start_foreground_service),
        summary = stringResource(R.string.settings_start_foreground_service_summary),
        key = "StartForegroundService",
        defaultValue = false,
        enabled = false,
    )
}

@Preview(showBackground = true)
@Composable
fun SingleLineSettingsItemPreview() {
    SettingsItem(title = stringResource(R.string.settings_start_foreground_service)) {}
}
