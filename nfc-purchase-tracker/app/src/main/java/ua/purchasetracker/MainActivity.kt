package ua.purchasetracker

import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Стан діалогу додавання/редагування. */
data class EditRequest(
    val existing: Purchase? = null,
    val amount: String = "",
    val merchant: String = "",
    val currency: String = "UAH",
    val source: String = "Вручну",
    val tagId: String? = null,
)

enum class Period(val label: String) { TODAY("Сьогодні"), WEEK("Тиждень"), MONTH("Місяць"), ALL("Усе") }

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private val editRequest = mutableStateOf<EditRequest?>(null)
    private val listenerEnabled = mutableStateOf(false)
    private val nfcState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PurchaseRepository.init(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        handleIntent(intent)
        setContent {
            AppTheme {
                MainScreen(
                    editRequest = editRequest,
                    listenerEnabled = listenerEnabled.value,
                    nfcState = nfcState.value,
                    onOpenListenerSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onOpenNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                    onExport = ::exportCsv,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerEnabled.value = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val adapter = nfcAdapter
        nfcState.value = when {
            adapter == null -> "NFC недоступний на цьому пристрої"
            !adapter.isEnabled -> "NFC вимкнено"
            else -> ""
        }
        // Поки додаток відкритий, телефон читає NFC-мітки (наприклад, наліпку біля каси/кавоварки).
        if (adapter != null && adapter.isEnabled) {
            adapter.enableReaderMode(
                this,
                ::onTagScanned,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                null,
            )
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.let { if (it.isEnabled) it.disableReaderMode(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** NFC-мітка з URI purchase://add?amount=..&merchant=.. відкрила додаток. */
    @Suppress("DEPRECATION")
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return
        val uri = intent.data ?: return
        val tag: Tag? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        editRequest.value = requestFromUri(uri, tag?.id?.toHex())
    }

    /** Викликається на фоновому потоці NFC при піднесенні мітки до телефона. */
    private fun onTagScanned(tag: Tag) {
        val tagId = tag.id?.toHex() ?: return
        val uri = try {
            Ndef.get(tag)?.cachedNdefMessage?.records?.firstNotNullOfOrNull { it.toUri() }
        } catch (_: Exception) {
            null
        }
        val request = if (uri != null && uri.scheme == "purchase") {
            requestFromUri(uri, tagId)
        } else {
            EditRequest(
                merchant = Prefs.tagMerchant(this, tagId).orEmpty(),
                source = "NFC-мітка",
                tagId = tagId,
            )
        }
        runOnUiThread { editRequest.value = request }
    }

    private fun requestFromUri(uri: Uri, tagId: String?): EditRequest {
        val merchant = uri.getQueryParameter("merchant")
            ?: tagId?.let { Prefs.tagMerchant(this, it) }
            ?: ""
        return EditRequest(
            amount = uri.getQueryParameter("amount").orEmpty(),
            merchant = merchant,
            currency = uri.getQueryParameter("currency")?.uppercase() ?: "UAH",
            source = "NFC-мітка",
            tagId = tagId,
        )
    }

    private fun exportCsv() {
        val csv = PurchaseRepository.toCsv(PurchaseRepository.purchases.value)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Мої покупки.csv")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        startActivity(Intent.createChooser(send, "Експорт покупок"))
    }
}

private fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF8AB4FF))
        else -> lightColorScheme(primary = Color(0xFF1259C3))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    editRequest: MutableState<EditRequest?>,
    listenerEnabled: Boolean,
    nfcState: String,
    onOpenListenerSettings: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onExport: () -> Unit,
) {
    val ctx = LocalContext.current
    val purchases by PurchaseRepository.purchases.collectAsStateWithLifecycle()
    var period by remember { mutableStateOf(Period.MONTH) }
    var menuOpen by remember { mutableStateOf(false) }
    var captureAll by remember { mutableStateOf(Prefs.captureAllApps(ctx)) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val from = periodStart(period)
    val filtered = purchases.filter { it.timestamp >= from }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Мої покупки") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Експорт CSV") },
                            onClick = { menuOpen = false; onExport() },
                        )
                        DropdownMenuItem(
                            text = { Text("Доступ до сповіщень") },
                            onClick = { menuOpen = false; onOpenListenerSettings() },
                        )
                        DropdownMenuItem(
                            text = { Text("Читати всі додатки") },
                            leadingIcon = { Checkbox(checked = captureAll, onCheckedChange = null) },
                            onClick = {
                                captureAll = !captureAll
                                Prefs.setCaptureAllApps(ctx, captureAll)
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editRequest.value = EditRequest() }) {
                Icon(Icons.Filled.Add, contentDescription = "Додати покупку")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!listenerEnabled) {
                item {
                    WarningCard(
                        text = "Щоб автоматично записувати NFC-оплати, дозвольте додатку читати сповіщення " +
                            "Samsung Wallet / Google Wallet / банку.",
                        button = "Надати доступ",
                        onClick = onOpenListenerSettings,
                    )
                }
            }
            if (nfcState.isNotEmpty()) {
                item { WarningCard(text = nfcState, button = "Налаштування NFC", onClick = onOpenNfcSettings) }
            }
            item { SummaryCard(period, filtered) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Period.entries.forEach { p ->
                        FilterChip(selected = p == period, onClick = { period = p }, label = { Text(p.label) })
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "Поки що немає покупок. Оплатіть телефоном або годинником через NFC — " +
                            "покупка з'явиться тут автоматично. Або натисніть «+».",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val byDay = filtered.groupBy { dayLabel(it.timestamp) }
            byDay.forEach { (day, list) ->
                item(key = "h_$day") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp, end = 8.dp)) {
                        Text(day, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            totalsText(list),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                item(key = "c_$day") {
                    Card(shape = RoundedCornerShape(24.dp)) {
                        list.forEach { p -> PurchaseRow(p) { editRequest.value = p.toEditRequest() } }
                    }
                }
            }
        }
    }

    editRequest.value?.let { req ->
        EditDialog(req, onDismiss = { editRequest.value = null })
    }
}

@Composable
private fun WarningCard(text: String, button: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text(button) }
        }
    }
}

@Composable
private fun SummaryCard(period: Period, list: List<Purchase>) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Витрачено · ${period.label.lowercase()}", style = MaterialTheme.typography.labelLarge)
            val totals = totalsByCurrency(list)
            if (totals.isEmpty()) {
                Text(PaymentParser.format(0, "UAH"), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            } else {
                totals.forEachIndexed { i, (cur, sum) ->
                    Text(
                        PaymentParser.format(sum, cur),
                        fontSize = if (i == 0) 34.sp else 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val count = list.size
            val avg = totals.firstOrNull()?.let { (cur, sum) ->
                val n = list.count { it.currency == cur }
                " · середня ${PaymentParser.format(sum / n, cur)}"
            }.orEmpty()
            Text("Покупок: $count$avg", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PurchaseRow(p: Purchase, onClick: () -> Unit) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(p.timestamp))
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(p.merchant ?: "Покупка") },
        supportingContent = { Text("${p.source} · $time") },
        trailingContent = {
            Text(
                PaymentParser.format(p.amountMinor, p.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDialog(req: EditRequest, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var amount by remember(req) { mutableStateOf(req.amount) }
    var merchant by remember(req) { mutableStateOf(req.merchant) }
    var currency by remember(req) { mutableStateOf(req.currency) }
    var rememberTag by remember(req) { mutableStateOf(req.tagId != null) }
    val minor = PaymentParser.toMinor(amount.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    req.existing != null -> "Редагувати покупку"
                    req.tagId != null -> "Покупка з NFC-мітки"
                    else -> "Нова покупка"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Сума") },
                    singleLine = true,
                    isError = amount.isNotEmpty() && minor == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Магазин / опис") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("UAH", "USD", "EUR").forEach { c ->
                        FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) })
                    }
                }
                if (req.tagId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberTag, onCheckedChange = { rememberTag = it })
                        Text("Запам'ятати мітку для цього магазину")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = minor != null && minor > 0,
                onClick = {
                    val m = merchant.trim().ifEmpty { null }
                    if (req.existing != null) {
                        PurchaseRepository.update(req.existing.copy(amountMinor = minor!!, currency = currency, merchant = m))
                    } else {
                        PurchaseRepository.add(minor!!, currency, m, req.source)
                    }
                    if (req.tagId != null && rememberTag && m != null) Prefs.setTagMerchant(ctx, req.tagId, m)
                    onDismiss()
                },
            ) { Text("Зберегти") }
        },
        dismissButton = {
            Row {
                if (req.existing != null) {
                    TextButton(onClick = { PurchaseRepository.delete(req.existing.id); onDismiss() }) {
                        Text("Видалити", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Скасувати") }
            }
        },
    )
}

private fun Purchase.toEditRequest() = EditRequest(
    existing = this,
    amount = "%d.%02d".format(amountMinor / 100, amountMinor % 100),
    merchant = merchant.orEmpty(),
    currency = currency,
    source = source,
)

/** Список (валюта, сума), найбільша сума першою. */
fun totalsByCurrency(list: List<Purchase>): List<Pair<String, Long>> =
    list.groupBy { it.currency }
        .map { (cur, items) -> cur to items.sumOf { it.amountMinor } }
        .sortedByDescending { it.second }

private fun totalsText(list: List<Purchase>) =
    totalsByCurrency(list).joinToString(" + ") { (c, s) -> PaymentParser.format(s, c) }

fun periodStart(period: Period): Long {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    when (period) {
        Period.TODAY -> Unit
        Period.WEEK -> {
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            if (cal.timeInMillis > System.currentTimeMillis()) cal.add(Calendar.WEEK_OF_YEAR, -1)
        }
        Period.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
        Period.ALL -> return 0L
    }
    return cal.timeInMillis
}

private fun dayLabel(ts: Long): String {
    val today = periodStart(Period.TODAY)
    val day = 24L * 60 * 60 * 1000
    return when {
        ts >= today -> "Сьогодні"
        ts >= today - day -> "Вчора"
        else -> SimpleDateFormat("d MMMM, EEEE", Locale("uk")).format(Date(ts))
    }
}
