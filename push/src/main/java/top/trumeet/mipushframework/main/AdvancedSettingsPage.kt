package top.trumeet.mipushframework.main

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.catchingnow.icebox.sdk_client.IceBox
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.SettingUtils
import com.xiaomi.xmsf.utils.ConfigCenter
import top.trumeet.common.utils.Utils
import top.trumeet.mipushframework.component.MiuixPageScaffold
import top.trumeet.mipushframework.component.SettingsGroup
import top.trumeet.mipushframework.component.SettingsItem
import top.trumeet.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

class AdvancedSettingsPage : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Theme {
                window.navigationBarColor = MiuixTheme.colorScheme.surfaceContainer.toArgb()
                SettingsApp()
            }
        }
    }
}

@Composable
private fun SettingsApp() {
    MiuixPageScaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            color = MiuixTheme.colorScheme.background
        ) {
            SettingsScreen()
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column {
        CleanUpBlock()
        ExperimentalBlock()
        ConfigurationsBlock()
    }
}

@Composable
fun ConfigurationsBlock() {
    val context = LocalContext.current
    SettingsGroup(title = stringResource(R.string.settings_options)) {
        SettingsItem(
            title = stringResource(R.string.settings_notify_on_register),
            key = "NotificationOnRegister",
            defaultValue = false,
        )
        SettingsItem(
            title = stringResource(R.string.settings_show_loaded_file_after_configurations_loaded),
            key = "ShowConfigurationListOnLoaded",
            defaultValue = false,
        )
        SettingsItem(
            title = stringResource(R.string.settings_debug_mode),
            summary = stringResource(R.string.settings_debug_mode_summary),
            key = "DebugMode",
            defaultValue = false,
        )
        SettingsItem(
            title = stringResource(R.string.settings_show_all_events),
            key = "ShowAllEvents",
            defaultValue = false,
        )
        SettingsItem(
            title = stringResource(R.string.settings_start_foreground_service),
            summary = stringResource(R.string.settings_start_foreground_service_summary),
            key = "StartForegroundService",
            defaultValue = false,
        ) {
            SettingUtils.startMiPushServiceAsForegroundService(context)
        }
        SettingsItem(
            title = stringResource(R.string.pref_title_access_mode),
            summary = stringResource(R.string.pref_summary_access_mode),
            key = "AccessMode",
            values = stringArrayResource(R.array.pref_title_access_mode_list_titles),
            defaultValue = "0"
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val exactAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }
        SettingsItem(
            title = stringResource(R.string.settings_alarm_schedule_policy),
            summary = if (exactAllowed) {
                stringResource(R.string.settings_alarm_schedule_exact)
            } else {
                stringResource(R.string.settings_alarm_schedule_inexact)
            }
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (ignored: Exception) {}
                }
            }
        }
    }
}

@Composable
private fun ExperimentalBlock() {
    val context = LocalContext.current
    val focusProtocolVersion = remember {
        try {
            Settings.System.getInt(
                context.contentResolver,
                "notification_focus_protocol",
                0,
            )
        } catch (_: Throwable) {
            0
        }
    }
    var iceBoxGranted by remember {
        mutableStateOf(
            SettingUtils.isIceBoxInstalled()
                    && SettingUtils.iceBoxPermissionGranted(context)
        )
    }
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        iceBoxGranted = it.values.all { granted -> granted }
        setIceBoxSupported(context, iceBoxGranted)
    }

    SettingsGroup(title = stringResource(R.string.settings_experimental)) {
        SettingsItem(
            title = stringResource(R.string.settings_focus_protocol_status),
            summary = if (focusProtocolVersion > 0) {
                stringResource(
                    R.string.settings_focus_protocol_available,
                    focusProtocolVersion,
                )
            } else {
                stringResource(R.string.settings_focus_protocol_unavailable)
            },
        ) {}
        SettingsItem(
            title = stringResource(R.string.settings_mock_notification),
            summary = stringResource(R.string.settings_mock_notification_summary)
        ) {
            SettingUtils.notifyMockNotification(context)
        }
        SettingsItem(
            title = stringResource(R.string.settings_mock_focus_notification),
            summary = stringResource(R.string.settings_mock_focus_notification_summary)
        ) {
            SettingUtils.notifyMockFocusNotification(context)
        }

        SettingsItem(
            title = stringResource(R.string.settings_icebox_permission),
            summary = stringResource(R.string.settings_icebox_permission_summary),
            checked = iceBoxGranted,
            enabled = SettingUtils.isIceBoxInstalled()
        ) {
            if (!iceBoxGranted) {
                permissionsLauncher.launch(arrayOf(IceBox.SDK_PERMISSION))
            }
            iceBoxGranted = !iceBoxGranted
            setIceBoxSupported(context, iceBoxGranted)
        }
    }
}

private fun setIceBoxSupported(context: Context, iceBoxGranted: Boolean) {
    val preferences = ConfigCenter.getSharedPreferences(context)
    preferences.edit().putBoolean("IceboxSupported", iceBoxGranted).apply()
}

@Composable
private fun CleanUpBlock() {
    val context = LocalContext.current
    SettingsGroup(title = stringResource(R.string.settings_clear)) {
        SettingsItem(
            title = stringResource(R.string.settings_clear_history),
            summary = stringResource(R.string.settings_clear_history_summary)
        ) {
            SettingUtils.clearHistory(context)
        }

        SettingsItem(
            title = stringResource(R.string.settings_clear_log),
            summary = stringResource(R.string.settings_clear_log_summary)
        ) {
            SettingUtils.clearLog(context)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    Utils.context = LocalContext.current
    SettingsApp()
}
