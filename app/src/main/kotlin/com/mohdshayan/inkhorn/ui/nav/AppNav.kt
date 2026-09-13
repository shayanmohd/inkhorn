package com.mohdshayan.inkhorn.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mohdshayan.inkhorn.data.prefs.Settings
import com.mohdshayan.inkhorn.di.ServiceLocator
import com.mohdshayan.inkhorn.engine.Engine
import com.mohdshayan.inkhorn.ui.chronicle.ChronicleScreen
import com.mohdshayan.inkhorn.ui.components.LocalHaptics
import com.mohdshayan.inkhorn.ui.components.LocalSnack
import com.mohdshayan.inkhorn.ui.ending.EndingScreen
import com.mohdshayan.inkhorn.ui.ledger.LedgerScreen
import com.mohdshayan.inkhorn.ui.settings.LicencesScreen
import com.mohdshayan.inkhorn.ui.settings.PrivacyScreen
import com.mohdshayan.inkhorn.ui.settings.SettingsScreen
import com.mohdshayan.inkhorn.ui.theme.LocalInk
import com.mohdshayan.inkhorn.ui.theme.RadiusMd
import com.mohdshayan.inkhorn.ui.workshop.WorkshopScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable object LedgerRoute
@Serializable object WorkshopRoute
@Serializable object ChronicleRoute
@Serializable object SettingsRoute
@Serializable object PrivacyRoute
@Serializable object LicencesRoute
@Serializable data class EndingRoute(val endingId: String)

private data class Tab(val route: Any, val label: String, val icon: ImageVector)

/**
 * One activity, one NavHost. The bottom bar appears once the story has revealed a second
 * destination, and only on the three tab routes. The NavHost never changes shape, so rotation
 * across the 600dp breakpoint keeps every screen's state.
 */
@Composable
fun AppNav(settings: Settings?) {
    val nav = rememberNavController()
    val repo = ServiceLocator.repository
    val state by repo.state.collectAsStateWithLifecycle()
    val ink = LocalInk.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val show: (String) -> Unit = { message ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    val endingToShow by repo.endingToShow.collectAsStateWithLifecycle()
    LaunchedEffect(endingToShow) {
        endingToShow?.let { id ->
            repo.endingShown()
            nav.navigate(EndingRoute(id)) { launchSingleTop = true }
        }
    }

    val revealed = state?.revealed.orEmpty()
    val tabs = buildList {
        add(Tab(LedgerRoute, "Ledger", Icons.Rounded.AutoStories))
        if (Engine.TAB_WORKSHOP in revealed) add(Tab(WorkshopRoute, "Workshop", Icons.Rounded.Handyman))
        if (Engine.TAB_CHRONICLE in revealed) add(Tab(ChronicleRoute, "Chronicle", Icons.Rounded.HistoryEdu))
    }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onTab = destination == null || tabs.any { t -> destination.hasRoute(t.route::class) }

    CompositionLocalProvider(LocalSnack provides show, LocalHaptics provides (settings?.haptics ?: true)) {
        Scaffold(
            containerColor = ink.vellum,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(RadiusMd),
                        containerColor = ink.ironGall,
                        contentColor = ink.vellum,
                    )
                }
            },
            bottomBar = {
                if (tabs.size >= 2 && onTab) {
                    NavigationBar(containerColor = ink.leaf) {
                        for (t in tabs) {
                            val selected = destination?.hasRoute(t.route::class) ?: (t.route == LedgerRoute)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { nav.goToTab(t.route) },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ink.verdigris,
                                    selectedTextColor = ink.verdigris,
                                    indicatorColor = ink.verdigris.copy(alpha = 0.14f),
                                    unselectedIconColor = ink.fadedInk,
                                    unselectedTextColor = ink.fadedInk,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                NavHost(navController = nav, startDestination = LedgerRoute) {
                    composable<LedgerRoute> {
                        LedgerScreen(
                            contentPadding = padding,
                            firstLinePlayed = settings?.firstLinePlayed ?: true,
                            onOpenSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
                            onOpenEnding = { id -> nav.navigate(EndingRoute(id)) { launchSingleTop = true } },
                        )
                    }
                    composable<WorkshopRoute> {
                        WorkshopScreen(padding, onBackToLedger = { nav.backToLedger() })
                    }
                    composable<ChronicleRoute> {
                        ChronicleScreen(padding, onBackToLedger = { nav.backToLedger() })
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            contentPadding = padding,
                            onBack = { nav.popBackStack() },
                            onOpenPrivacy = { nav.navigate(PrivacyRoute) { launchSingleTop = true } },
                            onOpenLicences = { nav.navigate(LicencesRoute) { launchSingleTop = true } },
                            onLoaded = { nav.backToLedger() },
                        )
                    }
                    composable<PrivacyRoute> { PrivacyScreen(padding, onBack = { nav.popBackStack() }) }
                    composable<LicencesRoute> { LicencesScreen(padding, onBack = { nav.popBackStack() }) }
                    composable<EndingRoute> { e ->
                        EndingScreen(
                            endingId = e.toRoute<EndingRoute>().endingId,
                            contentPadding = padding,
                            onReturnToLedger = { nav.backToLedger() },
                        )
                    }
                }
            }
        }
    }
}

private fun NavHostController.goToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Pops back to the ledger without saving what was above it, so nothing is restored later by mistake. */
private fun NavHostController.backToLedger() {
    if (!popBackStack<LedgerRoute>(inclusive = false)) {
        navigate(LedgerRoute) { launchSingleTop = true }
    }
}
