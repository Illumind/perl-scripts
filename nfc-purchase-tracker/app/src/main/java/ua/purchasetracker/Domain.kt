package ua.purchasetracker

import java.util.Calendar
import java.util.TimeZone

data class Purchase(
    val id: Long,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val categoryId: Long,
    /** Останні 4 цифри картки або null. */
    val card: String?,
    /** Назва застосунку-джерела: "Samsung Wallet", "monobank", "Вручну"… */
    val source: String,
    val sourceType: SourceType,
    val timestamp: Long,
    val note: String? = null,
)

data class Category(
    val id: Long,
    /** Ключ вбудованої категорії (CategoryKeys) або null для створеної користувачем. */
    val key: String?,
    /** Назва створеної користувачем категорії (для вбудованих — null, береться з ресурсів). */
    val name: String?,
    val emoji: String,
    val color: Long,
    /** Місячний ліміт у мінорних одиницях базової валюти; 0 — без ліміту. */
    val budgetMinor: Long,
)

/** Діапазон часу [start, end). */
data class Range(val start: Long, val end: Long) {
    operator fun contains(ts: Long) = ts in start until end
}

/** Розрахунки для екранів «Головна», «Статистика», «Бюджет». Чистий Kotlin — покрито тестами. */
object Stats {

    /** Бюджетний місяць, що містить [now]; починається з [startDay] (1–28). */
    fun budgetMonth(now: Long, startDay: Int, tz: TimeZone = TimeZone.getDefault()): Range {
        val day = startDay.coerceIn(1, 28)
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.get(Calendar.DAY_OF_MONTH) < day) cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return Range(start, cal.timeInMillis)
    }

    /** Попередній/наступний бюджетний місяць відносно [r]. */
    fun shiftMonth(r: Range, startDay: Int, months: Int, tz: TimeZone = TimeZone.getDefault()): Range {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = r.start; add(Calendar.MONTH, months) }
        return budgetMonth(cal.timeInMillis, startDay, tz)
    }

    fun day(now: Long, tz: TimeZone = TimeZone.getDefault()): Range {
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return Range(start, cal.timeInMillis)
    }

    fun week(now: Long, tz: TimeZone = TimeZone.getDefault()): Range {
        val d = day(now, tz)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = d.start }
        val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Пн = 0
        cal.add(Calendar.DAY_OF_MONTH, -dow)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 7)
        return Range(start, cal.timeInMillis)
    }

    fun year(now: Long, tz: TimeZone = TimeZone.getDefault()): Range {
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = day(now, tz).start
            set(Calendar.DAY_OF_YEAR, 1)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.YEAR, 1)
        return Range(start, cal.timeInMillis)
    }

    fun inRange(list: List<Purchase>, r: Range, currency: String? = null) =
        list.filter { it.timestamp in r && (currency == null || it.currency == currency) }

    fun total(list: List<Purchase>): Long = list.sumOf { it.amountMinor }

    /** Категорія → сума, за спаданням. */
    fun byCategory(list: List<Purchase>): List<Pair<Long, Long>> =
        list.groupBy { it.categoryId }
            .map { (id, items) -> id to total(items) }
            .sortedByDescending { it.second }

    /** Сума по кожному дню діапазону (індекс 0 — перший день). */
    fun daily(list: List<Purchase>, r: Range, tz: TimeZone = TimeZone.getDefault()): List<Long> {
        val days = mutableListOf<Range>()
        var d = day(r.start, tz)
        while (d.start < r.end) {
            days += d
            d = day(d.end, tz)
        }
        return days.map { dr -> total(list.filter { it.timestamp in dr }) }
    }

    data class MerchantStat(val merchant: String, val total: Long, val count: Int)

    fun topMerchants(list: List<Purchase>, limit: Int = 10): List<MerchantStat> =
        list.filter { !it.merchant.isNullOrBlank() }
            .groupBy { CategoryClassifier.normalize(it.merchant!!) }
            .map { (_, items) -> MerchantStat(items.first().merchant!!.trim(), total(items), items.size) }
            .sortedByDescending { it.total }
            .take(limit)

    /** Суми за останні [count] бюджетних місяців, від найстаршого до поточного. */
    fun monthly(list: List<Purchase>, now: Long, startDay: Int, count: Int, tz: TimeZone = TimeZone.getDefault()): List<Pair<Range, Long>> {
        val current = budgetMonth(now, startDay, tz)
        return (count - 1 downTo 0).map { back ->
            val r = shiftMonth(current, startDay, -back, tz)
            r to total(list.filter { it.timestamp in r })
        }
    }

    /** Прогноз витрат на кінець місяця за поточним темпом. */
    fun forecast(spent: Long, month: Range, now: Long): Long {
        val elapsed = (now - month.start).coerceAtLeast(1)
        val length = month.end - month.start
        if (elapsed >= length) return spent
        return spent * length / elapsed
    }

    /** Чи щойно перетнули поріг (у відсотках) бюджету. */
    fun crossed(before: Long, after: Long, budget: Long, percent: Int): Boolean {
        if (budget <= 0) return false
        val threshold = budget * percent / 100
        return before < threshold && after >= threshold
    }
}

/** Гроші: форматування і розбір введення. Суми зберігаються в мінорних одиницях (копійках). */
object Money {
    val CURRENCIES = listOf("UAH", "PLN", "USD", "EUR", "GBP")

    fun format(minor: Long, currency: String): String = PaymentParser.format(minor, currency)

    /** Без копійок для великих сум на графіках/плитках: "12 480 ₴". */
    fun formatShort(minor: Long, currency: String): String {
        val full = format((minor / 100) * 100, currency)
        return full.replace(Regex(",00(?=\\u00A0)"), "")
    }

    fun parse(input: String): Long? = PaymentParser.toMinor(input.trim())

    fun toInput(minor: Long): String =
        if (minor % 100 == 0L) (minor / 100).toString() else "%d.%02d".format(minor / 100, minor % 100)
}
