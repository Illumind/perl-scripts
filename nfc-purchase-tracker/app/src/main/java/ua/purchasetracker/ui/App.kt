package ua.purchasetracker.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ua.purchasetracker.AppSettings
import ua.purchasetracker.Purchase
import ua.purchasetracker.R
import ua.purchasetracker.Repo

enum class Tab(@StringRes val label: Int) {
    HOME(R.string.tab_home), HISTORY(R.string.tab_history), STATS(R.string.tab_stats),
    BUDGET(R.string.tab_budget), SETTINGS(R.string.tab_settings),
}

/** Події з Activity (NFC-мітки) та стан доступів, які UI читає. */
object UiEvents {
    val editRequest = MutableStateFlow<EditRequest?>(null)
    val listenerEnabled = MutableStateFlow(true)
    val nfcState = MutableStateFlow<NfcState>(NfcState.OK)
}

enum class NfcState { OK, DISABLED, UNAVAILABLE }

class AppActions(
    val openListenerSettings: () -> Unit,
    val openNfcSettings: () -> Unit,
    val openLanguageSettings: () -> Unit,
    val exportCsv: () -> Unit,
    val requestNotificationPermission: () -> Unit,
)

@Composable
fun TapsumApp(actions: AppActions) {
    val ctx = LocalContext.current
    var onboarding by remember { mutableStateOf(!AppSettings.onboardingDone(ctx)) }
    if (onboarding) {
        Onboarding(actions) {
            AppSettings.setOnboardingDone(ctx)
            onboarding = false
        }
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val editRequest by UiEvents.editRequest.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMsg = stringResource(R.string.deleted)
    val undo = stringResource(R.string.undo)

    val onDeleted: (Purchase) -> Unit = { p ->
        scope.launch {
            if (snackbar.showSnackbar(deletedMsg, undo, withDismissAction = true) == SnackbarResult.ActionPerformed) {
                Repo.restore(p)
            }
        }
    }
    val openPurchase: (Purchase) -> Unit = { UiEvents.editRequest.value = EditRequest.from(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Відступ під статус-бар робить кожен екран сам (щоб фон ішов під нього).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == Tab.HOME || tab == Tab.HISTORY) {
                FloatingActionButton(
                    onClick = { UiEvents.editRequest.value = EditRequest() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_purchase)) }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t },
                        icon = { TabIcon(t) },
                        label = { Text(stringResource(t.label), maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen(actions, openPurchase, onOpenTab = { tab = it })
                Tab.HISTORY -> HistoryScreen(openPurchase)
                Tab.STATS -> StatsScreen()
                Tab.BUDGET -> BudgetScreen()
                Tab.SETTINGS -> SettingsScreen(actions)
            }
        }
    }

    editRequest?.let { req ->
        EditSheet(req, onDismiss = { UiEvents.editRequest.value = null }, onDeleted = onDeleted)
    }
}

@Composable
private fun TabIcon(t: Tab) {
    val vector: ImageVector? = when (t) {
        Tab.HOME -> Icons.Filled.Home
        Tab.HISTORY -> Icons.AutoMirrored.Filled.List
        Tab.SETTINGS -> Icons.Filled.Settings
        else -> null
    }
    if (vector != null) {
        Icon(vector, contentDescription = null)
    } else {
        val res = if (t == Tab.STATS) R.drawable.ic_tab_stats else R.drawable.ic_tab_wallet
        Icon(painterResource(res), contentDescription = null)
    }
}
