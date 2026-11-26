package ua.purchasetracker.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.purchasetracker.AppSettings
import ua.purchasetracker.Money
import ua.purchasetracker.R
import ua.purchasetracker.Range
import ua.purchasetracker.Repo
import ua.purchasetracker.Stats
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class StatsPeriod(val label: Int) { WEEK(R.string.week), MONTH(R.string.month), YEAR(R.string.year) }

@Composable
fun StatsScreen() {
    val ctx = LocalContext.current
    val purchases by Repo.purchases.collectAsStateWithLifecycle()
    val categories by Repo.categories.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(StatsPeriod.MONTH) }

    val now = System.currentTimeMillis()
    val base = AppSettings.baseCurrency(ctx)
    val startDay = AppSettings.monthStartDay(ctx)
    val range: Range = when (period) {
        StatsPeriod.WEEK -> Stats.week(now)
        StatsPeriod.MONTH -> Stats.budgetMonth(now, startDay)
        StatsPeriod.YEAR -> Stats.year(now)
    }
    val list = Stats.inRange(purchases, range, base)
    val total = Stats.total(list)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.tab_stats),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.statusBarsPadding().padding(top = 12.dp, start = 4.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsPeriod.entries.forEach { p ->
                    FilterChip(selected = p == period, onClick = { period = p }, label = { Text(stringResource(p.label)) })
                }
            }
        }

        // Кругова діаграма за категоріями
        item {
            SectionCard(title = stringResource(R.string.by_category)) {
                val byCat = Stats.byCategory(list)
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Donut(
                        byCat.map { (id, sum) -> sum to categories.firstOrNull { it.id == id }.colorValue() },
                        Modifier.size(180.dp),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.formatShort(total, base), style = AmountStyle.copy(fontSize = 20.sp))
                    }
                }
                byCat.forEach { (id, sum) ->
                    val c = categories.firstOrNull { it.id == id }
                    val pct = if (total > 0) (sum * 100 / total) else 0
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c.colorValue()))
                        Spacer(Modifier.width(10.dp))
                        Text("${c?.emoji ?: ""} ${categoryName(c)}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$pct%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(12.dp))
                        Text(Money.format(sum, base), style = AmountStyle.copy(fontSize = 14.sp))
                    }
                }
                if (byCat.isEmpty()) EmptyText()
            }
        }

        // Графік: по днях (тиждень/місяць) або по місяцях (рік)
        item {
            if (period == StatsPeriod.YEAR) {
                val cal = Calendar.getInstance().apply { timeInMillis = range.start }
                val months = (0 until 12).map { i ->
                    val c = cal.clone() as Calendar
                    c.add(Calendar.MONTH, i)
                    val start = c.timeInMillis
                    c.add(Calendar.MONTH, 1)
                    Range(start, c.timeInMillis)
                }
                val fmt = SimpleDateFormat("LLLLL", Locale.getDefault())
                SectionCard(title = stringResource(R.string.by_month)) {
                    Bars(
                        months.map { Stats.total(Stats.inRange(list, it)) },
                        Modifier.padding(horizontal = 16.dp),
                        highlight = months.indexOfFirst { now in it },
                        labels = months.map { fmt.format(Date(it.start)) },
                    )
                }
            } else {
                val daily = Stats.daily(list, range)
                val dayFmt = SimpleDateFormat(if (period == StatsPeriod.WEEK) "EE" else "d", Locale.getDefault())
                val labels = daily.indices.map { i ->
                    val ts = range.start + i * 86_400_000L + 3_600_000L
                    if (period == StatsPeriod.WEEK || i % 5 == 0) dayFmt.format(Date(ts)) else ""
                }
                SectionCard(title = stringResource(R.string.by_day)) {
                    Bars(daily, Modifier.padding(horizontal = 16.dp), highlight = daily.indices.firstOrNull { range.start + it * 86_400_000L <= now && now < range.start + (it + 1) * 86_400_000L } ?: -1, labels = labels)
                    val avg = if (daily.isNotEmpty()) total / daily.size else 0
                    Text(
                        stringResource(R.string.avg_per_day, Money.format(avg, base)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 10.dp),
                    )
                }
            }
        }

        // Порівняння місяців
        item {
            val monthly = Stats.monthly(purchases.filter { it.currency == base }, now, startDay, 6)
            SectionCard(title = stringResource(R.string.months_compare)) {
                val fmt = SimpleDateFormat("LLL", Locale.getDefault())
                Bars(
                    monthly.map { it.second },
                    Modifier.padding(horizontal = 16.dp),
                    highlight = monthly.lastIndex,
                    labels = monthly.map { fmt.format(Date(it.first.start)) },
                )
                val cur = monthly.last().second
                val prev = monthly[monthly.lastIndex - 1].second
                Spacer(Modifier.height(10.dp))
                val text = when {
                    prev == 0L -> stringResource(R.string.no_prev_month)
                    cur >= prev -> stringResource(R.string.more_than_prev, ((cur - prev) * 100 / prev).toInt())
                    else -> stringResource(R.string.less_than_prev, ((prev - cur) * 100 / prev).toInt())
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (prev > 0 && cur > prev) Brand.Warning else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        // Топ магазинів
        item {
            SectionCard(title = stringResource(R.string.top_merchants)) {
                val top = Stats.topMerchants(list, 10)
                if (top.isEmpty()) EmptyText()
                top.forEachIndexed { i, m ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = AmountStyle.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.n_purchases, m.count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Money.format(m.total, base), style = AmountStyle.copy(fontSize = 14.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyText() {
    Text(
        stringResource(R.string.no_data),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}
