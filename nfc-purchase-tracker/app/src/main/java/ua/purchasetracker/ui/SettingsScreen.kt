package ua.purchasetracker.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.purchasetracker.AppSettings
import ua.purchasetracker.BuildConfig
import ua.purchasetracker.Money
import ua.purchasetracker.PaymentParser
import ua.purchasetracker.R

@Composable
fun SettingsScreen(actions: AppActions) {
    val ctx = LocalContext.current
    val listenerEnabled by UiEvents.listenerEnabled.collectAsStateWithLifecycle()
    var captureAll by remember { mutableStateOf(AppSettings.captureAllApps(ctx)) }
    var disabled by remember { mutableStateOf(AppSettings.disabledApps(ctx)) }
    var baseCurrency by remember { mutableStateOf(AppSettings.baseCurrency(ctx)) }

    val installed = remember {
        PaymentParser.KNOWN_PAYMENT_APPS.filterKeys { pkg ->
            try {
                ctx.packageManager.getApplicationInfo(pkg, 0); true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.tab_settings),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.statusBarsPadding().padding(top = 12.dp, start = 4.dp),
            )
        }

        item {
            SectionCard(title = stringResource(R.string.auto_capture)) {
                SettingRow(
                    title = stringResource(R.string.notification_access),
                    subtitle = stringResource(if (listenerEnabled) R.string.enabled else R.string.disabled_tap_to_enable),
                    onClick = actions.openListenerSettings,
                )
                SettingRow(
                    title = stringResource(R.string.capture_all),
                    subtitle = stringResource(R.string.capture_all_hint),
                    checked = captureAll,
                    onCheckedChange = { captureAll = it; AppSettings.setCaptureAllApps(ctx, it) },
                )
            }
        }

        item {
            SectionCard(title = stringResource(R.string.sources)) {
                if (installed.isEmpty()) {
                    Text(
                        stringResource(R.string.no_sources_installed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                installed.forEach { (pkg, label) ->
                    SettingRow(
                        title = label,
                        checked = pkg !in disabled,
                        onCheckedChange = { on ->
                            AppSettings.setAppEnabled(ctx, pkg, on)
                            disabled = AppSettings.disabledApps(ctx)
                        },
                    )
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.base_currency)) {
                Text(
                    stringResource(R.string.base_currency_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Money.CURRENCIES.forEach { c ->
                        FilterChip(
                            selected = c == baseCurrency,
                            onClick = { baseCurrency = c; AppSettings.setBaseCurrency(ctx, c) },
                            label = { Text(c) },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.general)) {
                if (Build.VERSION.SDK_INT >= 33) {
                    SettingRow(stringResource(R.string.language), stringResource(R.string.language_hint), onClick = actions.openLanguageSettings)
                }
                SettingRow(stringResource(R.string.budget_alerts), stringResource(R.string.budget_alerts_hint), onClick = actions.requestNotificationPermission)
                SettingRow(stringResource(R.string.export_csv), stringResource(R.string.export_csv_hint), onClick = actions.exportCsv)
            }
        }

        item {
            SectionCard(title = stringResource(R.string.about)) {
                SettingRow("Tapsum ${BuildConfig.VERSION_NAME}", stringResource(R.string.about_text))
                SettingRow("Tapsum Plus", stringResource(R.string.plus_soon))
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { m ->
                when {
                    checked != null && onCheckedChange != null -> m.clickable { onCheckedChange(!checked) }
                    onClick != null -> m.clickable(onClick = onClick)
                    else -> m
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (checked != null) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
