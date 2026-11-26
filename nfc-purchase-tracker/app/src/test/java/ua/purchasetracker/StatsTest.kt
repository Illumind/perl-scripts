package ua.purchasetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StatsTest {
    private val tz = TimeZone.getTimeZone("Europe/Kyiv")

    private fun ts(y: Int, m: Int, d: Int, h: Int = 12): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    private fun p(amount: Long, t: Long, cat: Long = 1, merchant: String? = null) =
        Purchase(0, amount, "UAH", merchant, cat, null, "test", SourceType.CARD, t)

    @Test fun budgetMonthFromSalaryDay() {
        val r = Stats.budgetMonth(ts(2026, 11, 3), startDay = 5, tz = tz)
        assertEquals(ts(2026, 10, 5, 0), r.start)
        assertEquals(ts(2026, 11, 5, 0), r.end)
        val r2 = Stats.budgetMonth(ts(2026, 11, 5), startDay = 5, tz = tz)
        assertEquals(ts(2026, 11, 5, 0), r2.start)
    }

    @Test fun weekStartsMonday() {
        // 26.11.2026 — четвер
        val r = Stats.week(ts(2026, 11, 26), tz)
        assertEquals(ts(2026, 11, 23, 0), r.start)
    }

    @Test fun dailyAndCategories() {
        val month = Stats.budgetMonth(ts(2026, 11, 10), 1, tz)
        val list = listOf(p(100, ts(2026, 11, 1), 1), p(250, ts(2026, 11, 1), 2), p(50, ts(2026, 11, 3), 2))
        val daily = Stats.daily(list, month, tz)
        assertEquals(30, daily.size)
        assertEquals(350L, daily[0])
        assertEquals(50L, daily[2])
        assertEquals(listOf(2L to 300L, 1L to 100L), Stats.byCategory(list))
    }

    @Test fun topMerchantsMergesCase() {
        val t = ts(2026, 11, 1)
        val top = Stats.topMerchants(listOf(p(100, t, merchant = "АТБ"), p(200, t, merchant = "атб "), p(50, t, merchant = "Сільпо")))
        assertEquals(300L, top[0].total)
        assertEquals(2, top[0].count)
    }

    @Test fun monthlyComparison() {
        val list = listOf(p(100, ts(2026, 10, 15)), p(300, ts(2026, 11, 2)))
        val m = Stats.monthly(list, ts(2026, 11, 20), 1, 3, tz)
        assertEquals(listOf(0L, 100L, 300L), m.map { it.second })
    }

    @Test fun forecastAndThresholds() {
        val month = Range(0, 30)
        assertEquals(300L, Stats.forecast(100, month, 10))
        assertTrue(Stats.crossed(700, 850, 1000, 80))
        assertFalse(Stats.crossed(850, 900, 1000, 80))
        assertFalse(Stats.crossed(0, 900, 0, 80))
    }
}
