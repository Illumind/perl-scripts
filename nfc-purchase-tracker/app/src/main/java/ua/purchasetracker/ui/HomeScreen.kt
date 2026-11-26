package ua.purchasetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.purchasetracker.AppSettings
import ua.purchasetracker.Money
import ua.purchasetracker.Purchase
import ua.purchasetracker.R
import ua.purchasetracker.Repo
import ua.purchasetracker.Stats

@Composable
fun HomeScreen(actions: AppActions, openPurchase: (Purchase) -> Unit, onOpenTab: (Tab) -> Unit) {
    val ctx = LocalContext.current
    val purchases by Repo.purchases.collectAsStateWithLifecycle()
    val categories by Repo.categories.collectAsStateWithLifecycle()
    val listenerEnabled by UiEvents.listenerEnabled.collectAsStateWithLifecycle()
    val nfcState by UiEvents.nfcState.collectAsStateWithLifecycle()

    val now = System.currentTimeMillis()
    val base = AppSettings.baseCurrency(ctx)
    val month = Stats.budgetMonth(now, AppSettings.monthStartDay(ctx))
    val monthAll = Stats.inRange(purchases, month)
    val monthBase = monthAll.filter { it.currency == base }
    val spent = Stats.total(monthBase)
    val budget = AppSettings.monthlyBudget(ctx)
    val today = Stats.inRange(purchases, Stats.day(now))
    val otherCurrencies = monthAll.filter { it.currency != base }.groupBy { it.currency }
        .map { (c, l) -> Money.format(Stats.total(l), c) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text("Σ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 18.sp) }
                Spacer(Modifier.width(10.dp))
                Text("Tapsum", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (!listenerEnabled) {
            item {
                WarningCard(stringResource(R.string.warn_listener), stringResource(R.string.grant_access), actions.openListenerSettings)
            }
        }
        if (nfcState == NfcState.DISABLED) {
            item { WarningCard(stringResource(R.string.warn_nfc_off), stringResource(R.string.nfc_settings), actions.openNfcSettings) }
        }

        // Головна картка: витрачено за місяць + бюджет
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text(
                        stringResource(R.string.spent_in, monthName(month.start)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(Money.format(spent, base), style = AmountStyle.copy(fontSize = 38.sp))
                    if (otherCurrencies.isNotEmpty()) {
                        Text(
                            "+ " + otherCurrencies.joinToString(" + "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (budget > 0) {
                        BudgetBar(spent, budget)
                        Spacer(Modifier.height(8.dp))
                        val left = budget - spent
                        Row {
                            Text(
                                if (left >= 0) stringResource(R.string.left_of, Money.format(left, base), Money.formatShort(budget, base))
                                else stringResource(R.string.over_by, Money.format(-left, base)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (left >= 0) MaterialTheme.colorScheme.onSurface else Brand.Danger,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        val forecast = Stats.forecast(spent, month, now)
                        Text(
                            stringResource(R.string.forecast, Money.formatShort(forecast, base)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (forecast > budget) Brand.Warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            stringResource(R.string.set_budget_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenTab(Tab.BUDGET) }
                                .padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        // Сьогодні + кількість
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniTile(
                    title = stringResource(R.string.today),
                    value = Money.format(Stats.total(today.filter { it.currency == base }), base),
                    modifier = Modifier.weight(1f),
                )
                MiniTile(
                    title = stringResource(R.string.purchases_count),
                    value = monthAll.size.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Категорії місяця
        if (monthBase.isNotEmpty()) {
            item {
                SectionCard(
                    title = stringResource(R.string.by_category),
                    action = stringResource(R.string.more),
                    onAction = { onOpenTab(Tab.STATS) },
                ) {
                    val byCat = Stats.byCategory(monthBase)
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Donut(
                            byCat.map { (id, sum) -> sum to categories.firstOrNull { it.id == id }.colorValue() },
                            Modifier.size(110.dp),
                            stroke = 16.dp,
                        )
                        Spacer(Modifier.width(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            byCat.take(4).forEach { (id, sum) ->
                                val c = categories.firstOrNull { it.id == id }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(c.colorValue()))
                                    Spacer(Modifier.width(8.dp))
                                    Text(categoryName(c), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
                                    Spacer(Modifier.width(6.dp))
                                    Text(Money.formatShort(sum, base), style = AmountStyle.copy(fontSize = 13.sp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Останні покупки
        item {
            SectionCard(
                title = stringResource(R.string.recent),
                action = if (purchases.isNotEmpty()) stringResource(R.string.all) else null,
                onAction = { onOpenTab(Tab.HISTORY) },
            ) {
                if (purchases.isEmpty()) {
                    Text(
                        stringResource(R.string.empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                purchases.take(5).forEach { p ->
                    PurchaseRow(p, categories.firstOrNull { it.id == p.categoryId }) { openPurchase(p) }
                }
            }
        }
    }
}

@Composable
private fun MiniTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = AmountStyle.copy(fontSize = 20.sp))
        }
    }
}
