package ua.purchasetracker

/**
 * Результат розбору сповіщення про оплату.
 * Суму зберігаємо в копійках/центах (Long), щоб не накопичувати похибки Double.
 */
data class ParsedPayment(
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    /** Останні 4 цифри картки, якщо є в тексті. */
    val card: String? = null,
    val sourceType: SourceType = SourceType.CARD,
)

/** Спосіб оплати. */
enum class SourceType { NFC, CARD, ONLINE, MANUAL }

/**
 * Розпізнає оплати з тексту сповіщень Samsung Wallet, Google Wallet і банківських додатків.
 * Третім застосункам Android не віддає дані безконтактної оплати напряму,
 * тому джерело даних — сповіщення, які гаманець/банк показує після NFC-оплати.
 */
object PaymentParser {

    /** Пакети, які за замовчуванням вважаємо платіжними. */
    val KNOWN_PAYMENT_APPS: Map<String, String> = linkedMapOf(
        "com.samsung.android.spay" to "Samsung Wallet",
        "com.samsung.android.samsungpay.gear" to "Samsung Wallet (Watch)",
        "com.google.android.apps.walletnfcrel" to "Google Wallet",
        "com.ftband.mono" to "monobank",
        "ua.privatbank.ap24" to "Приват24",
        "com.ideabank.izibank" to "izibank",
        "ua.com.abank" to "A-Bank",
        "ua.raiffeisen.myraif" to "Raiffeisen",
        "com.pumb.mobile" to "ПУМБ",
        "ua.oschadbank.online" to "Ощадбанк",
        "com.sensebank.mobile" to "Sense Bank",
        "ua.otpbank.android" to "OTP Bank",
        "com.ukrsibbank.client.android" to "UKRSIBBANK",
        "ua.pravex.mobile" to "Правекс",
        "com.revolut.revolut" to "Revolut",
        "com.wise.android" to "Wise",
        "pl.pkobp.iko" to "PKO BP",
        "pl.mbank" to "mBank",
        "pl.ing.mojeing" to "ING",
        "pl.bzwbk.bzwbk24" to "Santander",
        "eu.eleader.mobilebanking.pekao" to "Pekao",
        "wit.android.bcpBankingApp.millenniumPL" to "Millennium",
    )

    /** Гаманці: оплата з них — завжди безконтактна (NFC). */
    val WALLET_APPS = setOf(
        "com.samsung.android.spay",
        "com.samsung.android.samsungpay.gear",
        "com.google.android.apps.walletnfcrel",
    )

    private val currencyAliases: List<Pair<String, String>> = listOf(
        "₴" to "UAH", "грн." to "UAH", "грн" to "UAH", "uah" to "UAH",
        "$" to "USD", "usd" to "USD",
        "€" to "EUR", "eur" to "EUR",
        "zł" to "PLN", "pln" to "PLN",
        "£" to "GBP", "gbp" to "GBP",
    )

    private const val CUR =
        "(₴|\\$|€|£|zł|(?<!\\p{L})(?:грн\\.?|uah|usd|eur|pln|gbp)(?!\\p{L}))"

    // 1 234,56 | 1 234.56 | 1,234.56 | 1.234,56 | 1234,5 | 1234
    private const val NUM =
        "(\\d{1,3}(?:[ \\u00A0\\u202F]\\d{3})+(?:[.,]\\d{1,2})?" +
            "|\\d{1,3}(?:,\\d{3})+\\.\\d{1,2}" +
            "|\\d{1,3}(?:\\.\\d{3})+,\\d{1,2}" +
            "|\\d+(?:[.,]\\d{1,2})?)"

    private val amountRegex = Regex(
        // валюта перед числом: "₴ 120.50", "UAH -120"   | число перед валютою: "-120,50 грн"
        "(?:$CUR\\s?([-−–])?\\s?(?<![\\d.,])$NUM(?![\\d]))" +
            "|(?:([-−–])?\\s?(?<![\\d.,])$NUM(?![\\d])\\s?$CUR)",
        RegexOption.IGNORE_CASE,
    )

    private val paymentKeywords = listOf(
        "оплат", "покуп", "списан", "сплач", "платіж", "витрат", "транзакц",
        "payment", "paid", "purchase", "spent", "charged", "transaction", "debit",
        "оплач", "покупк", "списание", "оплата",
        "płatność", "platnosc", "zakup", "transakcja", "obciążenie", "zapłacono", "blik",
    )

    private val onlineKeywords = listOf(
        "онлайн", "інтернет", "online", "internet", "e-commerce", "в мережі", "w internecie",
    )

    private val incomeKeywords = listOf(
        "зарахув", "надійш", "надходж", "поповн", "повернен", "відшкод", "кешбек", "cashback",
        "refund", "received", "top up", "top-up", "deposit", "отримано", "переказ від",
        "пополнен", "зачислен", "возврат",
        "wpływ", "wplyw", "uznanie", "zwrot", "przelew przychodzący", "doładowanie",
    )

    private val balanceMarkers = listOf(
        "баланс", "залишок", "доступно", "ліміт", "balance", "available", "limit", "остаток",
        "saldo", "dostępne", "dostepne", "бал.",
    )

    // "5168**1234", "*1234", "•• 1234"
    private val cardMaskRegex = Regex("[*•∙·]{1,6}\\s?(\\d{4})(?!\\d)")
    // "Картка 1234", "card 1234", "karta: 1234"
    private val cardWordRegex = Regex("(?:картк\\p{L}*|card|karta)\\s*[:№#]?\\s*(\\d{4})(?!\\d)", RegexOption.IGNORE_CASE)

    private val merchantRegexes = listOf(
        Regex("(?:merchant|магазин|продавець|отримувач)\\s*[:\\-]\\s*([^\\n,;]{2,40})", RegexOption.IGNORE_CASE),
        Regex("(?<!\\p{L})at\\s+([^\\n,;.]{2,40})", RegexOption.IGNORE_CASE),
    )

    /**
     * @param packageName пакет застосунку-джерела
     * @param title заголовок сповіщення
     * @param text текст сповіщення (EXTRA_TEXT / EXTRA_BIG_TEXT)
     * @param appLabel людська назва застосунку (для відкидання заголовків типу "Samsung Wallet")
     */
    fun parse(packageName: String, title: String?, text: String?, appLabel: String? = null): ParsedPayment? {
        val full = listOfNotNull(title, text).joinToString("\n").trim()
        if (full.isEmpty()) return null
        val lower = full.lowercase()

        if (incomeKeywords.any { lower.contains(it) }) return null

        val match = findSpendAmount(full) ?: return null
        val hasKeyword = paymentKeywords.any { lower.contains(it) }
        val isKnownApp = packageName in KNOWN_PAYMENT_APPS
        // Без ключового слова приймаємо лише суму зі знаком мінус від відомого банку/гаманця
        // (наприклад, monobank: "Сільпо  −245.50₴").
        if (!hasKeyword && !(isKnownApp && match.negative)) return null

        val sourceType = when {
            packageName in WALLET_APPS -> SourceType.NFC
            onlineKeywords.any { lower.contains(it) } -> SourceType.ONLINE
            else -> SourceType.CARD
        }
        return ParsedPayment(
            amountMinor = match.minor,
            currency = match.currency,
            merchant = extractMerchant(title, text, appLabel ?: KNOWN_PAYMENT_APPS[packageName]),
            card = (cardMaskRegex.find(full) ?: cardWordRegex.find(full))?.groupValues?.get(1),
            sourceType = sourceType,
        )
    }

    private data class AmountMatch(val minor: Long, val currency: String, val negative: Boolean)

    private fun findSpendAmount(full: String): AmountMatch? {
        for (m in amountRegex.findAll(full)) {
            val before = full.substring(maxOf(0, m.range.first - 20), m.range.first).lowercase()
            if (balanceMarkers.any { before.contains(it) }) continue

            val g = m.groupValues
            val (curRaw, sign, num) = if (g[1].isNotEmpty()) {
                Triple(g[1], g[2], g[3])
            } else {
                Triple(g[6], g[4], g[5])
            }
            val minor = toMinor(num) ?: continue
            if (minor <= 0) continue
            val prefixMinus = full.getOrNull(m.range.first - 1)?.let { it == '-' || it == '−' } == true
            return AmountMatch(minor, normalizeCurrency(curRaw), sign.isNotEmpty() || prefixMinus)
        }
        return null
    }

    fun normalizeCurrency(raw: String): String {
        val r = raw.lowercase().trim()
        return currencyAliases.firstOrNull { it.first == r }?.second ?: r.uppercase()
    }

    /** "1 234,56" → 123456; "1,234.5" → 123450; "45" → 4500. */
    fun toMinor(raw: String): Long? {
        var s = raw.replace(" ", "").replace(" ", "").replace(" ", "")
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val decimalSep = maxOf(lastDot, lastComma)
        val intPart: String
        var fracPart = ""
        if (decimalSep >= 0 && s.length - decimalSep - 1 in 1..2) {
            intPart = s.substring(0, decimalSep).replace(".", "").replace(",", "")
            fracPart = s.substring(decimalSep + 1)
        } else {
            intPart = s.replace(".", "").replace(",", "")
        }
        s = intPart
        if (s.isEmpty() || !s.all { it.isDigit() }) return null
        val major = s.toLongOrNull() ?: return null
        val frac = fracPart.padEnd(2, '0').take(2).toLongOrNull() ?: 0L
        return major * 100 + frac
    }

    private fun extractMerchant(title: String?, text: String?, appLabel: String?): String? {
        val body = text.orEmpty()
        for (r in merchantRegexes) {
            r.find(body)?.groupValues?.get(1)?.trim()?.let { if (it.isNotBlank()) return it }
        }
        val t = title?.trim().orEmpty()
        if (t.isEmpty()) return null
        val tl = t.lowercase()
        val generic = appLabel != null && tl.contains(appLabel.lowercase())
        val looksLikeAmount = amountRegex.containsMatchIn(t)
        val isKeywordOnly = paymentKeywords.any { tl.startsWith(it) } && t.length < 25
        return if (generic || looksLikeAmount || isKeywordOnly) null else t.take(40)
    }

    /** Форматування: 123456, "UAH" → "1 234,56 ₴". */
    fun format(amountMinor: Long, currency: String): String {
        val negative = amountMinor < 0
        val abs = kotlin.math.abs(amountMinor)
        val major = (abs / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
        val minor = (abs % 100).toString().padStart(2, '0')
        val symbol = when (currency) {
            "UAH" -> "₴"; "USD" -> "$"; "EUR" -> "€"; "PLN" -> "zł"; "GBP" -> "£"; else -> currency
        }
        return (if (negative) "-" else "") + "$major,$minor $symbol"
    }
}
