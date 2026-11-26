package ua.purchasetracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.purchasetracker.AppSettings
import ua.purchasetracker.Money
import ua.purchasetracker.Purchase
import ua.purchasetracker.R
import ua.purchasetracker.Repo
import ua.purchasetracker.SourceType
import ua.purchasetracker.Stats

@Composable
fun HistoryScreen(openPurchase: (Purchase) -> Unit) {
    val ctx = LocalContext.current
    val purchases by Repo.purchases.collectAsStateWithLifecycle()
    val categories by Repo.categories.collectAsStateWithLifecycle()

    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var sourceFilter by rememberSaveable { mutableStateOf<SourceType?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var catMenu by rememberSaveable { mutableStateOf(false) }

    val startDay = AppSettings.monthStartDay(ctx)
    val month = Stats.shiftMonth(Stats.budgetMonth(System.currentTimeMillis(), startDay), startDay, monthOffset)
    val q = query.trim().lowercase()
    val filtered = Stats.inRange(purchases, month).filter { p ->
        (sourceFilter == null || p.sourceType == sourceFilter) &&
            (categoryFilter == null || p.categoryId == categoryFilter) &&
            (q.isEmpty() || p.merchant.orEmpty().lowercase().contains(q) || p.note.orEmpty().lowercase().contains(q) ||
                p.source.lowercase().contains(q))
    }
    val today = stringResource(R.string.today)
    val yesterday = stringResource(R.string.yesterday)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthOffset-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
                Text(
                    monthName(month.start).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { monthOffset++ }, enabled = monthOffset < 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sourceFilter == null, onClick = { sourceFilter = null }, label = { Text(stringResource(R.string.all)) })
                SourceType.entries.forEach { t ->
                    FilterChip(selected = sourceFilter == t, onClick = { sourceFilter = if (sourceFilter == t) null else t }, label = { Text(sourceTypeLabel(t)) })
                }
                Box {
                    val cat = categories.firstOrNull { it.id == categoryFilter }
                    FilterChip(
                        selected = cat != null,
                        onClick = { catMenu = true },
                        label = { Text(cat?.let { "${it.emoji} ${categoryName(it)}" } ?: stringResource(R.string.category)) },
                    )
                    DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.all)) }, onClick = { categoryFilter = null; catMenu = false })
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.emoji}  ${categoryName(c)}") },
                                onClick = { categoryFilter = c.id; catMenu = false },
                            )
                        }
                    }
                }
            }
        }
        item {
            val totals = filtered.groupBy { it.currency }.map { (c, l) -> Money.format(Stats.total(l), c) }
            Text(
                stringResource(R.string.total_n, filtered.size, if (totals.isEmpty()) "0" else totals.joinToString(" + ")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (filtered.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.nothing_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        val base = AppSettings.baseCurrency(ctx)
        filtered.groupBy { Stats.day(it.timestamp).start }.forEach { (dayStart, list) ->
            item(key = "d$dayStart") {
                SectionCard(
                    title = dayHeader(dayStart, today, yesterday),
                    action = Money.format(Stats.total(list.filter { it.currency == base }), base),
                    onAction = {},
                ) {
                    list.forEach { p -> PurchaseRow(p, categories.firstOrNull { it.id == p.categoryId }) { openPurchase(p) } }
                }
            }
        }
    }
}
