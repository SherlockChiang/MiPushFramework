package top.trumeet.mipushframework.main.subpage

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nihility.Global
import com.nihility.InternalMessenger
import com.xiaomi.push.service.XMPushServiceMessenger
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.SettingUtils
import com.xiaomi.xmsf.push.utils.ConfigurationDiagnosticsSnapshot
import com.xiaomi.xmsf.push.utils.Configurations
import top.trumeet.common.utils.Utils
import top.trumeet.mipushframework.MainPageOperation
import top.trumeet.mipushframework.component.SettingsGroup
import top.trumeet.mipushframework.component.SettingsItem
import top.trumeet.mipushframework.component.MiuixActionButton
import top.trumeet.mipushframework.component.MiuixInput
import top.trumeet.mipushframework.main.AdvancedSettingsPage
import top.trumeet.mipushframework.main.HelpPage
import top.trumeet.mipushframework.utils.NotificationPermissionController
import top.trumeet.mipushframework.utils.NotificationPermissionPolicy
import top.trumeet.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun Settings(
    floatingBottomNav: Boolean = true,
    onFloatingBottomNavChange: ((Boolean) -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        color = MiuixTheme.colorScheme.background
    ) {
        SettingsScreen(
            floatingBottomNav = floatingBottomNav,
            onFloatingBottomNavChange = onFloatingBottomNavChange,
        )
    }
}


@Composable
private fun SettingsScreen(
    floatingBottomNav: Boolean,
    onFloatingBottomNavChange: ((Boolean) -> Unit)?,
) {
    Column {
        AppearanceBlock(
            floatingBottomNav = floatingBottomNav,
            onFloatingBottomNavChange = onFloatingBottomNavChange,
        )
        ServiceConfigurationBlock()
        DebugBlock()
        AboutBlock()
        if (floatingBottomNav) {
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun AppearanceBlock(
    floatingBottomNav: Boolean,
    onFloatingBottomNavChange: ((Boolean) -> Unit)?,
) {
    SettingsGroup(title = stringResource(R.string.settings_appearance)) {
        SettingsItem(
            title = stringResource(R.string.settings_floating_bottom_navigation),
            summary = stringResource(R.string.settings_floating_bottom_navigation_summary),
            checked = floatingBottomNav,
            onClick = {
                onFloatingBottomNavChange?.invoke(!floatingBottomNav)
            }
        )
    }
}

@Composable
private fun ServiceConfigurationBlock() {
    val context = LocalContext.current
    var diagnosticsRefreshToken by remember { mutableStateOf(0) }

    SettingsGroup(title = stringResource(R.string.settings_service_setting)) {
        SettingsItem(
            title = stringResource(R.string.settings_service_advance_setting),
            summary = stringResource(R.string.settings_summary_service_advance_setting)
        ) {
            context.startActivity(Intent(context, AdvancedSettingsPage::class.java))
        }

        NotificationPermissionItem()
        SetConfigurationsDirectory {
            diagnosticsRefreshToken += 1
        }
        ConfigurationDiagnosticsItem(
            refreshToken = diagnosticsRefreshToken,
            onRefresh = { diagnosticsRefreshToken += 1 },
        )
        SetXMPPServer(context)
    }
}

@Composable
private fun NotificationPermissionItem() {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember {
        mutableStateOf(
            activity?.let { NotificationPermissionController.status(it) }
                ?: NotificationPermissionPolicy.Status.BLOCKED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        NotificationPermissionController.markRequested(context)
        if (activity != null) {
            status = NotificationPermissionController.status(activity)
        }
    }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && activity != null) {
                status = NotificationPermissionController.status(activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val summary = when (status) {
        NotificationPermissionPolicy.Status.NOT_REQUIRED ->
            stringResource(R.string.settings_notification_permission_not_required)
        NotificationPermissionPolicy.Status.GRANTED ->
            stringResource(R.string.settings_notification_permission_granted)
        NotificationPermissionPolicy.Status.REQUESTABLE ->
            stringResource(R.string.settings_notification_permission_request)
        NotificationPermissionPolicy.Status.DENIED_CAN_ASK_AGAIN ->
            stringResource(R.string.settings_notification_permission_denied)
        NotificationPermissionPolicy.Status.BLOCKED ->
            stringResource(R.string.settings_notification_permission_blocked)
    }

    SettingsItem(
        title = stringResource(R.string.settings_notification_permission),
        summary = summary,
    ) {
        when (status) {
            NotificationPermissionPolicy.Status.REQUESTABLE,
            NotificationPermissionPolicy.Status.DENIED_CAN_ASK_AGAIN -> {
                NotificationPermissionController.markRequested(context)
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> NotificationPermissionController.openNotificationSettings(context)
        }
    }
}

@Composable
private fun SetXMPPServer(context: Context) {
    var currentXMPPServer by remember { mutableStateOf("") }
    DisposableEffect(context) {
        val messenger = object : InternalMessenger(context) {
            init {
                register(IntentFilter(XMPushServiceMessenger.IntentSetConnectionStatus))
                addListener { intent: Intent ->
                    val host = intent.getStringExtra("host")
                    if (!host.isNullOrEmpty()) currentXMPPServer = host
                }
                send(Intent(XMPushServiceMessenger.IntentGetConnectionStatus))
            }
        }
        onDispose { messenger.close() }
    }
    var text by remember { mutableStateOf(SettingUtils.getXMPPServer(context) ?: "") }
    SettingsItem(title = stringResource(R.string.settings_XMPP_server),
        summary = stringResource(R.string.settings_XMPP_server_summary) +
                "\nSet: [${SettingUtils.getXMPPServer(context) ?: ""}]" +
                "\nCurrent: [$currentXMPPServer]",
        confirmButton = { dismiss: () -> Unit ->
            MiuixActionButton(onClick = {
                SettingUtils.setXMPPServer(context, text)
                SettingUtils.sendXMPPReconnectRequest(context)
                currentXMPPServer = text
                dismiss()
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        onDismiss = {
            text = ""
        },
        content = {
            MiuixInput(
                value = text,
                onValueChange = { text = it },
                label = SettingUtils.getXMPPServerHint(),
                singleLine = true
            )
        })
}

@Composable
private fun SetConfigurationsDirectory(onConfigurationChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            SettingUtils.setConfigurationDirectory(context, uri)
            Global.ConfigCenter().loadConfigurations(context)
            onConfigurationChanged()
        }
    }

    SettingsItem(
        title = stringResource(R.string.settings_configuration_directory),
        summary = SettingUtils.getConfigurationDirectory(context)?.path
    ) {
        launcher.launch(null)
    }
}

/**
 * Shows configuration-reference diagnostics as a regular Miuix settings item.
 *
 * The snapshot is read from the configuration singleton only from a coroutine launched by an
 * explicit refresh key. This keeps SAF timestamp checks and configuration I/O out of Compose
 * recomposition, while still updating after a directory selection or a manual re-check.
 */
@Composable
private fun ConfigurationDiagnosticsItem(
    refreshToken: Int,
    onRefresh: () -> Unit,
) {
    var snapshot by remember {
        mutableStateOf(ConfigurationDiagnosticsSnapshot.notConfigured())
    }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshToken) {
        refreshing = true
        snapshot = withContext(Dispatchers.IO) {
            Configurations.getInstance().getDiagnosticsSnapshot()
        }
        refreshing = false
    }

    val summary = when {
        refreshing -> stringResource(R.string.settings_configuration_diagnostics_checking)
        snapshot.getStatus() == ConfigurationDiagnosticsSnapshot.Status.NOT_CONFIGURED ->
            stringResource(R.string.settings_configuration_diagnostics_not_configured)
        snapshot.getStatus() == ConfigurationDiagnosticsSnapshot.Status.FAILED ->
            stringResource(R.string.settings_configuration_diagnostics_failed)
        snapshot.getUnresolvedReferences().isEmpty() ->
            stringResource(R.string.settings_configuration_diagnostics_ready)
        else ->
            stringResource(
                R.string.settings_configuration_diagnostics_missing,
                snapshot.getUnresolvedReferences().size,
            )
    }

    SettingsItem(
        title = stringResource(R.string.settings_configuration_diagnostics),
        summary = summary,
        confirmButton = { dismiss ->
            MiuixActionButton(onClick = {
                onRefresh()
                dismiss()
            }) {
                Text(stringResource(R.string.settings_configuration_diagnostics_refresh))
            }
        },
        content = {
            if (refreshing) {
                Text(
                    text = stringResource(R.string.settings_configuration_diagnostics_checking),
                    style = MiuixTheme.textStyles.body2,
                )
            } else {
                ConfigurationDiagnosticsDetails(snapshot)
            }
        },
    )
}

@Composable
private fun ConfigurationDiagnosticsDetails(snapshot: ConfigurationDiagnosticsSnapshot) {
    when (snapshot.getStatus()) {
        ConfigurationDiagnosticsSnapshot.Status.NOT_CONFIGURED -> Text(
            text = stringResource(R.string.settings_configuration_diagnostics_not_configured),
            style = MiuixTheme.textStyles.body2,
        )
        ConfigurationDiagnosticsSnapshot.Status.FAILED -> Text(
            text = stringResource(R.string.settings_configuration_diagnostics_failed),
            style = MiuixTheme.textStyles.body2,
        )
        ConfigurationDiagnosticsSnapshot.Status.READY -> {
            val references = snapshot.getUnresolvedReferences()
            if (references.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_configuration_diagnostics_ready),
                    style = MiuixTheme.textStyles.body2,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(references) { reference ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            DiagnosticValue(
                                label = stringResource(
                                    R.string.settings_configuration_diagnostics_source,
                                ),
                                value = sanitizeDiagnosticValue(reference.getSourceName()),
                            )
                            DiagnosticValue(
                                label = stringResource(
                                    R.string.settings_configuration_diagnostics_owner,
                                ),
                                value = sanitizeDiagnosticValue(reference.getOwnerKey()),
                            )
                            DiagnosticValue(
                                label = stringResource(
                                    R.string.settings_configuration_diagnostics_reference,
                                ),
                                value = sanitizeDiagnosticValue(reference.getReference()),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticValue(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurface,
    )
}

/** Keep diagnostics safe and bounded even if a malformed config contains control characters. */
private fun sanitizeDiagnosticValue(value: String, maxLength: Int = 240): String {
    val sanitized = buildString {
        value.forEach { character ->
            append(if (character.isISOControl()) '\uFFFD' else character)
        }
    }
    return if (sanitized.length <= maxLength) sanitized else sanitized.take(maxLength - 1) + '\u2026'
}

@Composable
private fun DebugBlock() {
    val context = LocalContext.current

    SettingsGroup(title = stringResource(R.string.settings_debug)) {
        SettingsItem(
            title = stringResource(R.string.settings_get_log),
            summary = stringResource(R.string.settings_get_log_summary)
        ) {
            SettingUtils.shareLogs(context)
        }

        SettingsItem(
            title = stringResource(R.string.try_to_force_register_all_applications)
        ) {
            SettingUtils.tryForceRegisterAllApplications()
        }
    }
}

@Composable
private fun AboutBlock() {
    val context = LocalContext.current
    val mainPageOperation = MainPageOperation(context)

    SettingsGroup(title = stringResource(R.string.action_about)) {
        SettingsItem(
            title = stringResource(R.string.helplib_title)
        ) {
            context.startActivity(Intent(context, HelpPage::class.java))
        }

        SettingsItem(
            title = stringResource(R.string.action_update)
        ) {
            mainPageOperation.gotoGitHubReleasePage()
            Toast.makeText(context, R.string.update_toast, Toast.LENGTH_LONG).show()
        }

        SettingsItem(
            title = stringResource(R.string.action_about)
        ) {
            mainPageOperation.showAboutDialog()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPagePreview() {
    Utils.context = LocalContext.current
    Settings()
}
