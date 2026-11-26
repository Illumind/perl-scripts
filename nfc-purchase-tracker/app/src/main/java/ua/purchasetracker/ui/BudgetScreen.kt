package ua.purchasetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.purchasetracker.AppSettings
import ua.purchasetracker.Category
import ua.purchasetracker.CategoryKeys
import ua.purchasetracker.Money
import ua.purchasetracker.R
import ua.purchasetracker.Repo
import ua.purchasetracker.Stats

private val PALETTE = listOf(
    0xFF3CF2B0, 0xFF14B8E6, 0xFFFFB547, 0xFFFF7A45, 0xFFFF5C8A, 0xFFB57CFF,
    0xFF6C8CFF, 0xFFFFD84D, 0xFF4DD8FF, 0xFFFF8FD8, 0xFF8A94A6, 0xFF7BE37B,
)
private val EMOJIS = listOf("🛒", "☕", "🍔", "🚕", "⛽", "💊", "👕", "🏠", "🎬", "📺", "🎁", "📦", "🐶", "👶", "📚", "✈️", "💄", "🏋️", "💻", "🔧", "🍷", "🚬", "💡", "📱")

@Composable
fun BudgetScreen() {
    val ctx = LocalContext.current
    val purchases by Repo.purchases.collectAsStateWithLifecycle()
    val categories by Repo.categories.collectAsStateWithLifecycle()
    val base = AppSettings.baseCurrency(ctx)
    // Налаштування не є Flow — тримаємо локальну копію, щоб UI оновлювався одразу.
    var monthly by remember { mutableLongStateOf(AppSettings.monthlyBudget(ctx)) }
    var startDay by remember { mutableIntStateOf(AppSettings.monthStartDay(ctx)) }
    var editTotal by remember { mutableStateOf(false) }
    var editStart by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<Category?>(null) }
    var newCategory by remember { mutableStateOf(false) }

    val month = Stats.budgetMonth(System.currentTimeMillis(), startDay)
    val monthList = Stats.inRange(purchases, month, base)
    val spent = Stats.total(monthList)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.tab_budget),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.statusBarsPadding().padding(top = 12.dp, start = 4.dp),
            )
        }
        item {
            SectionCard(
                title = stringResource(R.string.monthly_budget),
                action = stringResource(R.string.edit),
                onAction = { editTotal = true },
            ) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (monthly > 0) {
                        Text(
                            "${Money.format(spent, base)} / ${Money.formatShort(monthly, base)}",
                            style = AmountStyle.copy(fontSize = 24.sp),
                        )
                        Spacer(Modifier.height(10.dp))
                        BudgetBar(spent, monthly)
                        Spacer(Modifier.height(8.dp))
                        val left = monthly - spent
                        Text(
                            if (left >= 0) stringResource(R.string.left, Money.format(left, base))
                            else stringResource(R.string.over_by, Money.format(-left, base)),
                            color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else Brand.Danger,
                        )
                    } else {
                        Text(stringResource(R.string.no_budget), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { editTotal = true }) { Text(stringResource(R.string.set_budget)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.month_starts, startDay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { editStart = true }.padding(vertical = 4.dp),
                    )
                }
            }
        }
        item {
            SectionCard(
                title = stringResource(R.string.category_budgets),
                action = stringResource(R.string.add_category),
                onAction = { newCategory = true },
            ) {
                categories.forEach { c ->
                    val catSpent = Stats.total(monthList.filter { it.categoryId == c.id })
                    Column(
                        Modifier.fillMaxWidth().clickable { editCategory = c }.padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(c, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(categoryName(c), modifier = Modifier.weight(1f))
                            Text(
                                if (c.budgetMinor > 0) "${Money.formatShort(catSpent, base)} / ${Money.formatShort(c.budgetMinor, base)}"
                                else Money.formatShort(catSpent, base),
                                style = AmountStyle.copy(fontSize = 14.sp),
                            )
                        }
                        if (c.budgetMinor > 0) {
                            Spacer(Modifier.height(8.dp))
                            BudgetBar(catSpent, c.budgetMinor, Modifier.padding(start = 48.dp), height = 6.dp)
                        }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.budget_notifications_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }

    if (editTotal) {
        AmountDialog(
            title = stringResource(R.string.monthly_budget),
            initial = monthly,
            currency = base,
            onDismiss = { editTotal = false },
            onSave = { AppSettings.setMonthlyBudget(ctx, it); monthly = it; editTotal = false },
        )
    }
    if (editStart) {
        NumberDialog(
            title = stringResource(R.string.month_start_day),
            initial = startDay,
            range = 1..28,
            onDismiss = { editStart = false },
            onSave = { AppSettings.setMonthStartDay(ctx, it); startDay = it; editStart = false },
        )
    }
    editCategory?.let { c ->
        CategoryDialog(c, base, onDismiss = { editCategory = null })
    }
    if (newCategory) {
        CategoryDialog(null, base, onDismiss = { newCategory = false })
    }
}

@Composable
private fun AmountDialog(title: String, initial: Long, currency: String, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (initial > 0) Money.toInput(initial) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("${stringResource(R.string.amount)}, $currency") },
                placeholder = { Text("0 = " + stringResource(R.string.no_limit)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(if (text.isBlank()) 0L else Money.parse(text) ?: 0L) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NumberDialog(title: String, initial: Int, range: IntRange, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(2) },
                singleLine = true,
                isError = value == null || value !in range,
                supportingText = { Text("${range.first}–${range.last}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(enabled = value != null && value in range, onClick = { onSave(value!!) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Створення (c == null) або редагування категорії: назва, емодзі, колір, ліміт. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(c: Category?, currency: String, onDismiss: () -> Unit) {
    val builtIn = c?.key != null
    val label = categoryName(c)
    var name by remember { mutableStateOf(if (c == null) "" else label) }
    var emoji by remember { mutableStateOf(c?.emoji ?: "📦") }
    var color by remember { mutableLongStateOf(c?.color ?: PALETTE.first()) }
    var limit by remember { mutableStateOf(c?.budgetMinor?.takeIf { it > 0 }?.let { Money.toInput(it) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c == null) stringResource(R.string.add_category) else label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!builtIn) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                }
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it },
                    label = { Text("${stringResource(R.string.monthly_limit)}, $currency") },
                    placeholder = { Text(stringResource(R.string.no_limit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EMOJIS.forEach { e ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(if (e == emoji) Color(color).copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 18.sp) }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.forEach { col ->
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Color(col)).clickable { color = col },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (col == color) Text("✓", color = Color(0xFF0E1218), fontSize = 14.sp)
                        }
                    }
                }
                if (c != null && c.key != CategoryKeys.OTHER) {
                    TextButton(onClick = { Repo.deleteCategory(c); onDismiss() }) {
                        Text(stringResource(R.string.delete_category), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = builtIn || name.isNotBlank(),
                onClick = {
                    val budget: Long = if (limit.isBlank()) 0L else Money.parse(limit) ?: 0L
                    if (c == null) {
                        Repo.addCategory(name.trim(), emoji, color, budget)
                    } else {
                        Repo.updateCategory(c.copy(name = if (builtIn) null else name.trim(), emoji = emoji, color = color, budgetMinor = budget))
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
