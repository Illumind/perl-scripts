package ua.purchasetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.purchasetracker.Category
import ua.purchasetracker.Money
import ua.purchasetracker.Purchase
import ua.purchasetracker.R
import ua.purchasetracker.SourceType
import ua.purchasetracker.categoryLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun categoryName(c: Category?): String = categoryLabel(LocalContext.current, c)

fun Category?.colorValue(): Color = Color(this?.color ?: 0xFF8A94A6)

/** Кругла «іконка» категорії з емодзі на кольоровому фоні. */
@Composable
fun CategoryBadge(c: Category?, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(c.colorValue().copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(c?.emoji ?: "📦", fontSize = (size.value * 0.48f).sp)
    }
}

/** Картка-секція у банківському стилі. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (action != null && onAction != null) {
                        Text(
                            action,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable(onClick = onAction).padding(4.dp),
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun sourceTypeLabel(t: SourceType): String = stringResource(
    when (t) {
        SourceType.NFC -> R.string.source_nfc
        SourceType.CARD -> R.string.source_card
        SourceType.ONLINE -> R.string.source_online
        SourceType.MANUAL -> R.string.source_manual
    }
)

@Composable
fun PurchaseRow(p: Purchase, category: Category?, onClick: () -> Unit) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(p.timestamp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.merchant ?: categoryName(category),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val card = p.card?.let { " · •$it" }.orEmpty()
            Text(
                "${sourceTypeLabel(p.sourceType)} · ${p.source}$card · $time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "−" + Money.format(p.amountMinor, p.currency),
            style = AmountStyle.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Смуга прогресу бюджету: зелена → жовта (≥80 %) → червона (≥100 %). */
@Composable
fun BudgetBar(spent: Long, budget: Long, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val ratio = if (budget > 0) spent.toFloat() / budget else 0f
    val color = when {
        ratio >= 1f -> Brand.Danger
        ratio >= 0.8f -> Brand.Warning
        else -> MaterialTheme.colorScheme.primary
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(color, size = Size(size.width * ratio.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}

/** Кільцева діаграма часток. */
@Composable
fun Donut(slices: List<Pair<Long, Color>>, modifier: Modifier = Modifier, stroke: Dp = 22.dp) {
    val total = slices.sumOf { it.first }.coerceAtLeast(1)
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val w = stroke.toPx()
        val d = size.minDimension - w
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        if (slices.isEmpty()) {
            drawArc(empty, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(w))
            return@Canvas
        }
        var start = -90f
        val gap = if (slices.size > 1) 2f else 0f
        for ((value, color) in slices) {
            val sweep = 360f * value / total
            drawArc(color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, topLeft, Size(d, d), style = Stroke(w, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

/** Стовпчиковий графік; [highlight] — індекс виділеного стовпця. */
@Composable
fun Bars(values: List<Long>, modifier: Modifier = Modifier, highlight: Int = -1, labels: List<String> = emptyList()) {
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barW = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            values.forEachIndexed { i, v ->
                val h = (size.height * v / max).coerceAtLeast(if (v > 0) 3.dp.toPx() else 1.dp.toPx())
                drawRoundRect(
                    color = if (highlight == -1 || i == highlight) primary else dim,
                    topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 3),
                )
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                labels.forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun WarningCard(text: String, button: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            FilledTonalButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text(button) }
        }
    }
}

/** Назва місяця в називному відмінку для поточної мови: "листопад", "November", "listopad". */
fun monthName(ts: Long): String =
    SimpleDateFormat("LLLL", Locale.getDefault()).format(Date(ts))

fun dayHeader(ts: Long, today: String, yesterday: String): String {
    val now = System.currentTimeMillis()
    val dayStart = ua.purchasetracker.Stats.day(now).start
    return when {
        ts >= dayStart -> today
        ts >= dayStart - 86_400_000L -> yesterday
        else -> SimpleDateFormat("d MMMM, EEEE", Locale.getDefault()).format(Date(ts))
    }
}
