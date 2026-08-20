package top.trumeet.mipushframework.main

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nihility.utils.RegistrationHelper
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.R
import top.trumeet.mipushframework.component.SettingsGroup
import top.trumeet.mipushframework.component.SettingsItem
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.db.RegisteredApplicationDb
import top.trumeet.mipush.provider.entities.RegisteredApplication
import top.trumeet.mipush.provider.entities.RegisteredApplication.RegisteredType
import top.trumeet.mipushframework.component.MarkdownView
import top.trumeet.mipushframework.component.MiuixActionButton
import top.trumeet.mipushframework.component.MiuixActionIconButton
import top.trumeet.mipushframework.component.MiuixPageScaffold
import top.trumeet.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class RegistrationAction {
    REGISTER,
    REREGISTER,
}

internal fun registrationActionFor(registeredType: Int): RegistrationAction? =
    when (registeredType) {
        RegisteredType.NotRegistered -> RegistrationAction.REGISTER
        RegisteredType.Unregistered -> RegistrationAction.REREGISTER
        else -> null
    }

class ApplicationInfoPage : ComponentActivity() {
    companion object {
        const val EXTRA_PACKAGE_NAME: String = "EXTRA_PACKAGE_NAME"
        const val EXTRA_IGNORE_NOT_REGISTERED: String = "EXTRA_IGNORE_NOT_REGISTERED"
    }

    private lateinit var applicationInfo: RegisteredApplication
    private lateinit var appConfigurationUtils: AppConfigurationUtils

    fun init(applicationInfo: RegisteredApplication) {
        this.applicationInfo = applicationInfo
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init(getRegisteredApplication()!!)
        setContent {
            Theme {
                window.navigationBarColor = MiuixTheme.colorScheme.surfaceContainer.toArgb()
                SettingsApp()
            }
        }
    }

    private fun getRegisteredApplication(): RegisteredApplication? {
        if (intent.hasExtra(EXTRA_PACKAGE_NAME)) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            var application = RegisteredApplicationDb.getRegisteredApplication(pkg)

            if (application == null &&
                intent.getBooleanExtra(EXTRA_IGNORE_NOT_REGISTERED, false)
            ) {
                application =
                    RegisteredApplication()
                application.packageName = pkg
                application.registeredType = RegisteredType.NotRegistered
            }
            return application
        }
        return null
    }

    @Composable
    fun SettingsApp() {
        if (!::appConfigurationUtils.isInitialized) {
            appConfigurationUtils =
                AppConfigurationUtils(
                    LocalContext.current,
                    applicationInfo
                )
        }

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
    fun SettingsScreen() {
        Column {
            ApplicationInfoHeader()
            Misc()
            NotificationChannels()
        }
    }

    @Composable
    fun ApplicationInfoHeader() {
        val context = LocalContext.current
        val isPreview = LocalInspectionMode.current
        val density = LocalDensity.current
        val icon = remember(applicationInfo.packageName, density) {
            val drawable = if (isPreview)
                AppCompatResources.getDrawable(context, android.R.mipmap.sym_def_app_icon)!!
            else applicationInfo.getIcon(context)
            val iconSizePx = with(density) { 40.dp.roundToPx() }
            drawable.toBitmap(iconSizePx, iconSizePx).asImageBitmap()
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = applicationInfo.appName,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = applicationInfo.packageName,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                MiuixActionIconButton(onClick = {
                    val uri = Uri.fromParts("package", applicationInfo.packageName, null)
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(uri)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) {
                    Image(
                        painterResource(R.drawable.ic_info),
                        stringResource(R.string.application_info_label),
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }

    @Composable
    private fun Misc() {
        Tips()
        RecentEvents()
        ShowRegistrationRequestSwitch()
    }

    @Composable
    private fun Tips() {
        val shouldSuggestFakeApp: Boolean =
            appConfigurationUtils.shouldSuggestFakeApp(applicationInfo.packageName)
        val context = LocalContext.current
        val onRegistrationAction = {
            RegistrationHelper(
                context,
                applicationInfo.packageName
            ).deleteRegistrationInfoAndRetryForceRegister()
        }

        when (registrationActionFor(applicationInfo.registeredType)) {
            RegistrationAction.REGISTER -> {
                val notRegisteredDesc = stringResource(
                    if (shouldSuggestFakeApp)
                        R.string.status_app_not_registered_detail_with_fake_suggest
                    else R.string.status_app_not_registered_detail_without_fake_suggest
                )
                Tips(
                    title = stringResource(R.string.status_app_not_registered_title),
                    description = notRegisteredDesc,
                    actionDescription = stringResource(R.string.registration_action_description),
                    actionLabel = stringResource(R.string.registration_action_register_now),
                    onAction = onRegistrationAction,
                )
            }

            RegistrationAction.REREGISTER -> Tips(
                title = stringResource(R.string.status_app_registered_error_title),
                description = stringResource(R.string.status_app_registered_error_desc),
                actionDescription = stringResource(R.string.registration_action_description),
                actionLabel = stringResource(R.string.registration_action_register_again),
                onAction = onRegistrationAction,
            )

            null -> Unit
        }
    }

    @Composable
    private fun RecentEvents() {
        SettingsItem(
            title = stringResource(R.string.recent_activity_view)
        ) {
            appConfigurationUtils.gotoRecentEventsPage()
        }
    }

    @Composable
    private fun ShowRegistrationRequestSwitch() {
        var checked by remember { mutableStateOf(applicationInfo.isNotificationOnRegister) }

        SettingsItem(
            title = stringResource(R.string.permission_notification_on_register),
            summary = stringResource(R.string.permission_summary_notification_on_register),
            checked = checked,
        ) {
            checked = !checked
            applicationInfo.isNotificationOnRegister = checked
        }
    }

    @Composable
    private fun NotificationChannels() {
        val isPreview = LocalInspectionMode.current
        if (Build.VERSION.SDK_INT >= VERSION_CODES.O && !isPreview) {
            AllNotificationChannels()
        } else {
            ManageNotificationItem()
        }
    }

    @RequiresApi(VERSION_CODES.O)
    @Composable
    private fun AllNotificationChannels() {
        val isPreview = LocalInspectionMode.current
        var groups: List<NotificationChannelGroup> = emptyList()
        var notificationChannels: List<NotificationChannel> = emptyList()

        if (!isPreview) {
            groups = appConfigurationUtils.notificationChannelGroups
            notificationChannels = appConfigurationUtils.notificationChannels!!
        }

        groups.forEach { group ->
            val categoryName = appConfigurationUtils.getNotificationCategoryName(group)
            NotificationCategory(
                categoryName,
                notificationChannels.filter { it.group == group.id })
        }
    }

    @Composable
    private fun NotificationCategory(categoryName: String, channels: List<NotificationChannel>) {
        SettingsGroup(categoryName) {
            channels.forEach { channel ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    SettingsItem(
                        title = AppConfigurationUtils.getNotificationTitle(
                            channel
                        ).toString(),
                        summary = AppConfigurationUtils.getNotificationSummary(
                            channel
                        ),
                        confirmButton = {},
                    ) {
                        NotificationChannel(channel, appConfigurationUtils)
                    }
                }
            }
        }
    }

    @Composable
    private fun ManageNotificationItem() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            SettingsItem(
                title = stringResource(R.string.settings_manage_app_notifications),
                summary = stringResource(R.string.settings_manage_app_notifications_summary),
                enabled = applicationInfo.registeredType == RegisteredType.NotRegistered,
            ) {
                appConfigurationUtils.gotoNotificationSettingPage()
            }
        }
    }

}

@Composable
fun Tips(
    title: String,
    description: String,
    actionDescription: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                painterResource(R.drawable.ic_error_outline_black_24dp), null,
                tint = Color(0xFFD50000)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MiuixTheme.textStyles.body2)

                MarkdownView(
                    description,
                    textSize = MiuixTheme.textStyles.footnote1.fontSize.value,
                )
                if (actionDescription != null && actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = actionDescription,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        modifier = Modifier.align(Alignment.End),
                        text = actionLabel,
                        onClick = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationChannel(
    channel: NotificationChannel, appConfigurationUtils: AppConfigurationUtils
) {
    Row {
        MiuixActionButton(onClick = {
            appConfigurationUtils.deleteNotificationChannel(channel)
        }) {
            Text(stringResource(R.string.notification_channels_delete))
        }
        MiuixActionButton(onClick = {
            appConfigurationUtils.copyToClipboard(channel)
        }) {
            Text(stringResource(R.string.notification_channels_copy_id))
        }
        MiuixActionButton(onClick = {
            appConfigurationUtils.gotoNotificationChannelSettingPage(
                channel,
                appConfigurationUtils.configApp
            )
        }) {
            Text(stringResource(R.string.notification_channels_setting))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TipsPreview() {
    Tips(
        stringResource(R.string.status_app_registered_error_title),
        stringResource(R.string.status_app_registered_error_desc)
    )
}

@RequiresApi(VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun NotificationChannelPreview() {
    val channel = NotificationChannel("123", "456", NotificationManager.IMPORTANCE_MIN)
    NotificationChannel(
        channel,
        AppConfigurationUtils(
            LocalContext.current,
            RegisteredApplication()
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    val context = LocalContext.current
    Utils.context = context

    val app = RegisteredApplication()
    app.packageName = BuildConfig.APPLICATION_ID
    app.appName = "test app"
    val page = ApplicationInfoPage()
    page.init(app)
    page.SettingsApp()
}
