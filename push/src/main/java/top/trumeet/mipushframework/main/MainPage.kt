package top.trumeet.mipushframework.main

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nihility.Global
import com.xiaomi.xmsf.R
import top.trumeet.mipushframework.MainPageUtils
import top.trumeet.mipushframework.component.MiuixBottomNavigation
import top.trumeet.mipushframework.component.MiuixPageScaffold
import top.trumeet.mipushframework.component.SearchBar
import top.trumeet.mipushframework.main.subpage.ApplicationList
import top.trumeet.mipushframework.main.subpage.ApplicationListPreview
import top.trumeet.mipushframework.main.subpage.EventDetailsDialogPreview
import top.trumeet.mipushframework.main.subpage.EventList
import top.trumeet.mipushframework.main.subpage.EventListPreview
import top.trumeet.mipushframework.main.subpage.Settings
import top.trumeet.mipushframework.main.subpage.SettingsPagePreview
import top.trumeet.mipushframework.utils.NotificationPermissionController
import top.trumeet.ui.theme.Theme
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

private var placeholder by mutableStateOf("Search...")

class MainPage : ComponentActivity() {
    private val mainPageUtils = MainPageUtils()
    private var notificationPermissionAttempted = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        NotificationPermissionController.markRequested(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        mainPageUtils.initOnCreate(applicationContext) { placeholder = it.toString() }
        setContent {
            Theme {
                window.navigationBarColor = MiuixTheme.colorScheme.surfaceContainer.toArgb()
                var floatingBottomNav by rememberSaveable {
                    mutableStateOf(Global.ConfigCenter().isFloatingBottomNavigation(applicationContext))
                }
                Main(
                    startDestination = Screen.Apps.route.toString(),
                    floatingBottomNav = floatingBottomNav,
                ) {
                    composable(Screen.Events.route.toString()) {
                        Column {
                            var query by rememberSaveable { mutableStateOf("") }
                            SearchBar(placeholder) { query = it }
                            EventList(query)
                        }
                    }
                    composable(Screen.Apps.route.toString()) {
                        Column {
                            var query by rememberSaveable { mutableStateOf("") }
                            SearchBar(placeholder) { query = it }
                            ApplicationList(query)
                        }
                    }
                    composable(Screen.Settings.route.toString()) {
                        Settings(
                            floatingBottomNav = floatingBottomNav,
                            onFloatingBottomNavChange = { enabled ->
                                floatingBottomNav = enabled
                                Global.ConfigCenter().setFloatingBottomNavigation(applicationContext, enabled)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (!notificationPermissionAttempted &&
            NotificationPermissionController.shouldAutoRequest(this)
        ) {
            notificationPermissionAttempted = true
            NotificationPermissionController.markRequested(this)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainPageUtils.close()
    }
}

private sealed class Screen(val route: Int, val icon: ImageVector) {
    object Events : Screen(R.string.main_event, eventIcon)
    object Apps : Screen(R.string.main_apps, appsIcon)
    object Settings : Screen(R.string.main_settings, settingsIcon)
}

private val eventIcon = ImageVector.Builder(
    name = "Events",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(17f, 10f); lineTo(7f, 10f); verticalLineTo(12f); horizontalLineTo(17f); close()
        moveTo(19f, 3f); horizontalLineTo(18f); verticalLineTo(1f); horizontalLineTo(16f)
        verticalLineTo(3f); horizontalLineTo(8f); verticalLineTo(1f); horizontalLineTo(6f)
        verticalLineTo(3f); horizontalLineTo(5f); curveTo(3.89f, 3f, 3.01f, 3.9f, 3.01f, 5f)
        lineTo(3f, 19f); curveTo(3f, 20.1f, 3.89f, 21f, 5f, 21f); horizontalLineTo(19f)
        curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f); verticalLineTo(5f)
        curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f); close()
        moveTo(19f, 19f); horizontalLineTo(5f); verticalLineTo(8f); horizontalLineTo(19f); close()
        moveTo(14f, 14f); horizontalLineTo(7f); verticalLineTo(16f); horizontalLineTo(14f); close()
    }
}.build()

private val appsIcon = ImageVector.Builder(
    name = "Applications",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 4f); horizontalLineTo(8f); verticalLineTo(8f); horizontalLineTo(4f); close()
        moveTo(10f, 4f); horizontalLineTo(14f); verticalLineTo(8f); horizontalLineTo(10f); close()
        moveTo(16f, 4f); horizontalLineTo(20f); verticalLineTo(8f); horizontalLineTo(16f); close()
        moveTo(4f, 10f); horizontalLineTo(8f); verticalLineTo(14f); horizontalLineTo(4f); close()
        moveTo(10f, 10f); horizontalLineTo(14f); verticalLineTo(14f); horizontalLineTo(10f); close()
        moveTo(16f, 10f); horizontalLineTo(20f); verticalLineTo(14f); horizontalLineTo(16f); close()
        moveTo(4f, 16f); horizontalLineTo(8f); verticalLineTo(20f); horizontalLineTo(4f); close()
        moveTo(10f, 16f); horizontalLineTo(14f); verticalLineTo(20f); horizontalLineTo(10f); close()
        moveTo(16f, 16f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(16f); close()
    }
}.build()

private val settingsIcon = ImageVector.Builder(
    name = "Settings",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(19.43f, 12.98f)
        curveTo(19.47f, 12.66f, 19.5f, 12.34f, 19.5f, 12f)
        curveTo(19.5f, 11.66f, 19.47f, 11.34f, 19.43f, 11.02f)
        lineTo(21.54f, 9.37f); curveTo(21.73f, 9.22f, 21.78f, 8.95f, 21.66f, 8.73f)
        lineTo(19.66f, 5.27f); curveTo(19.54f, 5.05f, 19.27f, 4.97f, 19.05f, 5.05f)
        lineTo(16.56f, 6.05f); curveTo(16.04f, 5.65f, 15.48f, 5.32f, 14.87f, 5.07f)
        lineTo(14.49f, 2.42f); curveTo(14.46f, 2.18f, 14.25f, 2f, 14f, 2f)
        horizontalLineTo(10f); curveTo(9.75f, 2f, 9.54f, 2.18f, 9.51f, 2.42f)
        lineTo(9.13f, 5.07f); curveTo(8.52f, 5.32f, 7.96f, 5.66f, 7.44f, 6.05f)
        lineTo(4.95f, 5.05f); curveTo(4.72f, 4.96f, 4.46f, 5.05f, 4.34f, 5.27f)
        lineTo(2.34f, 8.73f); curveTo(2.21f, 8.95f, 2.27f, 9.22f, 2.46f, 9.37f)
        lineTo(4.57f, 11.02f); curveTo(4.53f, 11.34f, 4.5f, 11.67f, 4.5f, 12f)
        curveTo(4.5f, 12.33f, 4.53f, 12.66f, 4.57f, 12.98f); lineTo(2.46f, 14.63f)
        curveTo(2.27f, 14.78f, 2.22f, 15.05f, 2.34f, 15.27f); lineTo(4.34f, 18.73f)
        curveTo(4.46f, 18.95f, 4.73f, 19.03f, 4.95f, 18.95f); lineTo(7.44f, 17.95f)
        curveTo(7.96f, 18.35f, 8.52f, 18.68f, 9.13f, 18.93f); lineTo(9.51f, 21.58f)
        curveTo(9.54f, 21.82f, 9.75f, 22f, 10f, 22f); horizontalLineTo(14f)
        curveTo(14.25f, 22f, 14.46f, 21.82f, 14.49f, 21.58f); lineTo(14.87f, 18.93f)
        curveTo(15.48f, 18.68f, 16.04f, 18.34f, 16.56f, 17.95f); lineTo(19.05f, 18.95f)
        curveTo(19.28f, 19.04f, 19.54f, 18.95f, 19.66f, 18.73f); lineTo(21.66f, 15.27f)
        curveTo(21.78f, 15.05f, 21.73f, 14.78f, 21.54f, 14.63f); close()
        moveTo(12f, 15.5f); curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12f)
        curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12f, 8.5f); curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12f)
        curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12f, 15.5f); close()
    }
}.build()

@Composable
fun BottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier,
    floating: Boolean = true,
    initialRoute: String? = null,
) {
    val items = listOf(
        Screen.Events, Screen.Apps, Screen.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val navigationItems = items.map { screen ->
        NavigationItem(
            label = stringResource(screen.route),
            icon = screen.icon,
        )
    }
    val selectedRoute = currentRoute ?: initialRoute
    val selected = items.indexOfFirst { it.route.toString() == selectedRoute }.coerceAtLeast(0)

    // Scaffold subcomposes the bottom bar before NavHost attaches its graph. Use the declared
    // start route for that frame, then recreate the indicator once the first real/restored
    // destination appears so cold start and state restoration never animate from a false tab.
    key(currentRoute != null) {
        MiuixBottomNavigation(
            modifier = modifier,
            items = navigationItems,
            selected = selected,
            onClick = { index ->
                val screen = items[index]
                navController.navigate(screen.route.toString()) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            floating = floating,
        )
    }
}

@Composable
private fun Main(
    startDestination: String,
    floatingBottomNav: Boolean = true,
    navContent: NavGraphBuilder.() -> Unit
) {
    val navController = rememberNavController()

    MiuixPageScaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (floatingBottomNav) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        // Miuix's floating navigation pattern keeps a small breathing room above
                        // the gesture bar; the bar itself owns its intrinsic width.
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    BottomNavigationBar(
                        navController = navController,
                        floating = true,
                        initialRoute = startDestination,
                    )
                }
            } else {
                BottomNavigationBar(
                    navController = navController,
                    modifier = Modifier.fillMaxWidth(),
                    floating = false,
                    initialRoute = startDestination,
                )
            }
        },
    ) { paddingValues ->
        NavHost(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues),
            navController = navController,
            startDestination = startDestination,
            builder = navContent
        )
    }
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
)
@Composable
private fun MainEventsPreview() {
    Main(Screen.Events.route.toString()) {
        composable(Screen.Events.route.toString()) {
            Column {
                val onValueChange: (String) -> Unit = {}
                SearchBar(placeholder, onValueChange)
                EventListPreview()
            }
        }
        composable(Screen.Apps.route.toString()) { }
        composable(Screen.Settings.route.toString()) { }
    }
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
)
@Composable
private fun MainAppsPreview() {
    Main(Screen.Apps.route.toString()) {
        composable(Screen.Events.route.toString()) { }
        composable(Screen.Apps.route.toString()) {
            Column {
                val onValueChange: (String) -> Unit = {}
                SearchBar(placeholder, onValueChange)
                ApplicationListPreview()
            }
        }
        composable(Screen.Settings.route.toString()) { }
    }
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
)
@Composable
private fun MainSettingsPreview() {
    Main(Screen.Settings.route.toString()) {
        composable(Screen.Events.route.toString()) { }
        composable(Screen.Apps.route.toString()) { }
        composable(Screen.Settings.route.toString()) { SettingsPagePreview() }
    }
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
)
@Composable
private fun MainDialogPreview() {
    Main(Screen.Events.route.toString()) {
        composable(Screen.Events.route.toString()) {
            EventDetailsDialogPreview()
        }
        composable(Screen.Apps.route.toString()) { }
        composable(Screen.Settings.route.toString()) { }
    }
}
