package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.CallShieldScreen
import com.example.ui.screens.PhoneControlScreen
import com.example.ui.theme.PmAiTheme
import com.example.viewmodel.InAppCommandEvent
import com.example.viewmodel.PmAiViewModel

object PmAiRoutes {
    const val ROUTE_ASSISTANT = "assistant"
    const val ROUTE_CONTROL = "control"
    const val ROUTE_CALLS = "calls"
    const val ROUTE_STUDIO = "studio"
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val NAV_ITEMS = listOf(
    NavDestinationItem(
        route = PmAiRoutes.ROUTE_ASSISTANT,
        label = "PM Brain",
        selectedIcon = Icons.Rounded.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_assistant"
    ),
    NavDestinationItem(
        route = PmAiRoutes.ROUTE_CONTROL,
        label = "Control",
        selectedIcon = Icons.Rounded.TouchApp,
        unselectedIcon = Icons.Outlined.TouchApp,
        testTag = "nav_control"
    ),
    NavDestinationItem(
        route = PmAiRoutes.ROUTE_CALLS,
        label = "Call Shield",
        selectedIcon = Icons.Rounded.Call,
        unselectedIcon = Icons.Outlined.Call,
        testTag = "nav_calls"
    ),
    NavDestinationItem(
        route = PmAiRoutes.ROUTE_STUDIO,
        label = "AI Studio",
        selectedIcon = Icons.Rounded.Palette,
        unselectedIcon = Icons.Outlined.Palette,
        testTag = "nav_studio"
    )
)

@Composable
fun PmAiApp(viewModel: PmAiViewModel) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val pendingPermission by viewModel.pendingPermission.collectAsStateWithLifecycle()
    val showAccessibilityPrompt by viewModel.showAccessibilityPrompt.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.clearPendingPermission()
        if (!granted) {
            // Surface helpful note if denied
        }
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    PmAiTheme(darkTheme = isDarkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 680.dp

            val navigateTo: (String) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            LaunchedEffect(Unit) {
                viewModel.inAppEvents.collect { event ->
                    if (event is InAppCommandEvent.NavigateTo) {
                        navigateTo(event.route)
                    }
                }
            }

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                        ) {
                            NAV_ITEMS.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label, maxLines = 1) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ) {
                            Spacer(Modifier.weight(0.1f))
                            NAV_ITEMS.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Sleek Compact Global Brand Strip
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "PM AI",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "ANDROID 16",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.toggleTheme() },
                                    modifier = Modifier.testTag("theme_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                        contentDescription = "Toggle light or dark theme"
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = PmAiRoutes.ROUTE_ASSISTANT
                            ) {
                                composable(PmAiRoutes.ROUTE_ASSISTANT) {
                                    AssistantScreen(
                                        viewModel = viewModel,
                                        onNavigateToControl = { navigateTo(PmAiRoutes.ROUTE_CONTROL) }
                                    )
                                }
                                composable(PmAiRoutes.ROUTE_CONTROL) {
                                    PhoneControlScreen(viewModel = viewModel)
                                }
                                composable(PmAiRoutes.ROUTE_CALLS) {
                                    CallShieldScreen(viewModel = viewModel)
                                }
                                composable(PmAiRoutes.ROUTE_STUDIO) {
                                    AiStudioScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Runtime Permission Prompt Dialog
        pendingPermission?.let { req ->
            AlertDialog(
                onDismissRequest = { viewModel.clearPendingPermission() },
                title = { Text("Permission Needed") },
                text = { Text(req.rationale) },
                confirmButton = {
                    Button(
                        onClick = {
                            runtimePermissionLauncher.launch(req.permission)
                        }
                    ) {
                        Text("Grant Permission")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearPendingPermission() }) {
                        Text("Not Now")
                    }
                }
            )
        }

        // Accessibility Service Setup Dialog
        if (showAccessibilityPrompt) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissAccessibilityPrompt() },
                title = { Text("Enable PM AI Phone Control") },
                text = {
                    Text(
                        "To perform real screen scrolling, auto-scroll, and OS navigation across apps, turn on 'PM AI Phone Control' inside Android Accessibility Settings."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissAccessibilityPrompt()
                            viewModel.openAccessibilitySettings()
                        }
                    ) {
                        Text("Open Accessibility")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissAccessibilityPrompt() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
