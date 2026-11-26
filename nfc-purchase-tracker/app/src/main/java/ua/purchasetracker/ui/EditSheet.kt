package ua.purchasetracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.purchasetracker.AppSettings
import ua.purchasetracker.CategoryClassifier
import ua.purchasetracker.CategoryKeys
import ua.purchasetracker.Money
import ua.purchasetracker.Purchase
import ua.purchasetracker.R
import ua.purchasetracker.Repo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Що відкрити у вікні редагування. */
data class EditRequest(
    val existing: Purchase? = null,
    val amount: String = "",
    val merchant: String = "",
    val currency: String? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val source: String? = null,
    /** ID NFC-мітки, якщо вікно відкрите піднесенням мітки. */
    val tagId: String? = null,
) {
    companion object {
        fun from(p: Purchase) = EditRequest(
            existing = p,
            amount = Money.toInput(p.amountMinor),
            merchant = p.merchant.orEmpty(),
            currency = p.currency,
            categoryId = p.categoryId,
            note = p.note.orEmpty(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditSheet(req: EditRequest, onDismiss: () -> Unit, onDeleted: (Purchase) -> Unit) {
    val ctx = LocalContext.current
    val categories by Repo.categories.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var amount by remember(req) { mutableStateOf(req.amount) }
    var merchant by remember(req) { mutableStateOf(req.merchant) }
    var currency by remember(req) { mutableStateOf(req.currency ?: AppSettings.baseCurrency(ctx)) }
    var note by remember(req) { mutableStateOf(req.note) }
    var rememberTag by remember(req) { mutableStateOf(true) }
    // null — категорію ще не вибрано вручну: підказуємо за назвою магазину.
    var pickedCategory by remember(req) { mutableStateOf(req.categoryId) }
    var guessKey by remember(req) { mutableStateOf(CategoryClassifier.classify(req.merchant)) }
    val minor = Money.parse(amount)
    val manualSource = stringResource(R.string.source_manual)
    val nfcTagSource = stringResource(R.string.source_nfc_tag)

    val effectiveCategory = pickedCategory
        ?: categories.firstOrNull { it.key == guessKey }?.id
        ?: categories.firstOrNull { it.key == CategoryKeys.OTHER }?.id

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(
                    when {
                        req.existing != null -> R.string.edit_purchase
                        req.tagId != null -> R.string.purchase_from_tag
                        else -> R.string.new_purchase
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            req.existing?.let { p ->
                val date = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(p.timestamp))
                Text(
                    "$date · ${sourceTypeLabel(p.sourceType)} · ${p.source}" + (p.card?.let { " · •$it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' || ch == ' ' } },
                label = { Text(stringResource(R.string.amount)) },
                singleLine = true,
                isError = amount.isNotEmpty() && minor == null,
                textStyle = AmountStyle.copy(fontSize = 28.sp, textAlign = TextAlign.Start),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Money.CURRENCIES.forEach { c ->
                    FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) })
                }
            }
            OutlinedTextField(
                value = merchant,
                onValueChange = {
                    merchant = it
                    guessKey = CategoryClassifier.classify(it)
                },
                label = { Text(stringResource(R.string.merchant)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            Text(stringResource(R.string.category), style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { c ->
                    val selected = c.id == effectiveCategory
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) c.colorValue().copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = if (selected) BorderStroke(1.5.dp, c.colorValue()) else null,
                        modifier = Modifier.clickable { pickedCategory = c.id },
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.emoji)
                            Spacer(Modifier.width(6.dp))
                            Text(categoryName(c), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (req.existing != null && pickedCategory != req.existing.categoryId && merchant.isNotBlank()) {
                Text(
                    stringResource(R.string.category_will_be_remembered, merchant.trim()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            if (req.tagId != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberTag, onCheckedChange = { rememberTag = it })
                    Text(stringResource(R.string.remember_tag))
                }
            }

            Button(
                enabled = minor != null && minor > 0,
                onClick = {
                    val m = merchant.trim().ifEmpty { null }
                    val n = note.trim().ifEmpty { null }
                    val existing = req.existing
                    if (existing != null) {
                        Repo.update(
                            existing.copy(
                                amountMinor = minor!!, currency = currency, merchant = m,
                                categoryId = effectiveCategory ?: existing.categoryId, note = n,
                            )
                        )
                    } else {
                        val source = req.source ?: if (req.tagId != null) nfcTagSource else manualSource
                        Repo.addManual(minor!!, currency, m, effectiveCategory, source, n)
                    }
                    if (req.tagId != null && rememberTag && m != null) AppSettings.setTagMerchant(ctx, req.tagId, m)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) { Text(stringResource(R.string.save), modifier = Modifier.padding(vertical = 6.dp)) }

            if (req.existing != null) {
                TextButton(
                    onClick = {
                        Repo.delete(req.existing.id)
                        onDeleted(req.existing)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.padding(4.dp))
        }
    }
}
