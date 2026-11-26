package ua.purchasetracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

data class Purchase(
    val id: Long,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    /** Звідки запис: "Samsung Wallet", "monobank", "NFC-мітка", "Вручну"… */
    val source: String,
    val timestamp: Long,
)

private class PurchaseDbHelper(context: Context) :
    SQLiteOpenHelper(context, "purchases.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE purchases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount_minor INTEGER NOT NULL,
                currency TEXT NOT NULL,
                merchant TEXT,
                source TEXT NOT NULL,
                ts INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_purchases_ts ON purchases(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/**
 * Єдине сховище покупок для UI та NotificationListener (обидва в одному процесі).
 */
object PurchaseRepository {
    private lateinit var helper: PurchaseDbHelper
    private val io = Executors.newSingleThreadExecutor()
    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    // Захист від дублів: сповіщення часто оновлюються/публікуються кілька разів.
    private var lastAutoKey: String? = null
    private var lastAutoTime = 0L

    @Synchronized
    fun init(context: Context) {
        if (::helper.isInitialized) return
        helper = PurchaseDbHelper(context.applicationContext)
        io.execute { reload() }
    }

    fun add(amountMinor: Long, currency: String, merchant: String?, source: String, ts: Long = System.currentTimeMillis()) {
        io.execute {
            helper.writableDatabase.insert("purchases", null, ContentValues().apply {
                put("amount_minor", amountMinor)
                put("currency", currency)
                put("merchant", merchant)
                put("source", source)
                put("ts", ts)
            })
            reload()
        }
    }

    /** Додати автоматично розпізнану оплату; повертає false, якщо це дубль. */
    @Synchronized
    fun addAuto(p: ParsedPayment, source: String): Boolean {
        val key = "$source|${p.amountMinor}|${p.currency}"
        val now = System.currentTimeMillis()
        if (key == lastAutoKey && now - lastAutoTime < 120_000) return false
        lastAutoKey = key
        lastAutoTime = now
        add(p.amountMinor, p.currency, p.merchant, source, now)
        return true
    }

    fun update(p: Purchase) {
        io.execute {
            helper.writableDatabase.update("purchases", ContentValues().apply {
                put("amount_minor", p.amountMinor)
                put("currency", p.currency)
                put("merchant", p.merchant)
            }, "id = ?", arrayOf(p.id.toString()))
            reload()
        }
    }

    fun delete(id: Long) {
        io.execute {
            helper.writableDatabase.delete("purchases", "id = ?", arrayOf(id.toString()))
            reload()
        }
    }

    private fun reload() {
        val list = mutableListOf<Purchase>()
        helper.readableDatabase.rawQuery(
            "SELECT id, amount_minor, currency, merchant, source, ts FROM purchases ORDER BY ts DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                list += Purchase(
                    id = c.getLong(0),
                    amountMinor = c.getLong(1),
                    currency = c.getString(2),
                    merchant = if (c.isNull(3)) null else c.getString(3),
                    source = c.getString(4),
                    timestamp = c.getLong(5),
                )
            }
        }
        _purchases.value = list
    }

    fun toCsv(list: List<Purchase>): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT)
        val sb = StringBuilder("date;amount;currency;merchant;source\n")
        for (p in list) {
            val amount = "%d.%02d".format(p.amountMinor / 100, p.amountMinor % 100)
            fun esc(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
            sb.append(fmt.format(java.util.Date(p.timestamp))).append(';')
                .append(amount).append(';').append(p.currency).append(';')
                .append(esc(p.merchant)).append(';').append(esc(p.source)).append('\n')
        }
        return sb.toString()
    }
}

/** Налаштування та прив'язки NFC-міток до магазинів. */
object Prefs {
    private const val FILE = "settings"

    fun captureAllApps(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("capture_all", false)

    fun setCaptureAllApps(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("capture_all", v).apply()

    fun tagMerchant(ctx: Context, tagId: String): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("tag_$tagId", null)

    fun setTagMerchant(ctx: Context, tagId: String, merchant: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("tag_$tagId", merchant).apply()
}
