package ua.purchasetracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/** Емодзі та кольори вбудованих категорій. */
private val DEFAULT_CATEGORIES: List<Triple<String, String, Long>> = listOf(
    Triple(CategoryKeys.GROCERIES, "🛒", 0xFF3CF2B0),
    Triple(CategoryKeys.CAFE, "☕", 0xFFFFB547),
    Triple(CategoryKeys.TRANSPORT, "🚕", 0xFF14B8E6),
    Triple(CategoryKeys.FUEL, "⛽", 0xFFFF7A45),
    Triple(CategoryKeys.HEALTH, "💊", 0xFFFF5C8A),
    Triple(CategoryKeys.CLOTHES, "👕", 0xFFB57CFF),
    Triple(CategoryKeys.HOME, "🏠", 0xFF6C8CFF),
    Triple(CategoryKeys.ENTERTAINMENT, "🎬", 0xFFFFD84D),
    Triple(CategoryKeys.SUBSCRIPTIONS, "📺", 0xFF4DD8FF),
    Triple(CategoryKeys.GIFTS, "🎁", 0xFFFF8FD8),
    Triple(CategoryKeys.OTHER, "📦", 0xFF8A94A6),
)

private class Db(context: Context) : SQLiteOpenHelper(context, "purchases.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE purchases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount_minor INTEGER NOT NULL,
                currency TEXT NOT NULL,
                merchant TEXT,
                source TEXT NOT NULL,
                ts INTEGER NOT NULL,
                category_id INTEGER NOT NULL DEFAULT 0,
                card TEXT,
                source_type TEXT NOT NULL DEFAULT 'CARD',
                note TEXT
            )"""
        )
        db.execSQL("CREATE INDEX idx_purchases_ts ON purchases(ts)")
        createV2Tables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE purchases ADD COLUMN category_id INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE purchases ADD COLUMN card TEXT")
            db.execSQL("ALTER TABLE purchases ADD COLUMN source_type TEXT NOT NULL DEFAULT 'CARD'")
            db.execSQL("ALTER TABLE purchases ADD COLUMN note TEXT")
            createV2Tables(db)
            // Розкласти старі записи за категоріями.
            val ids = categoryIdsByKey(db)
            db.rawQuery("SELECT id, merchant FROM purchases", null).use { c ->
                while (c.moveToNext()) {
                    val key = CategoryClassifier.classify(c.getStringOrNull(1))
                    db.execSQL("UPDATE purchases SET category_id = ? WHERE id = ?", arrayOf(ids[key], c.getLong(0)))
                }
            }
        }
    }

    private fun createV2Tables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                key TEXT UNIQUE,
                name TEXT,
                emoji TEXT NOT NULL,
                color INTEGER NOT NULL,
                budget_minor INTEGER NOT NULL DEFAULT 0,
                sort INTEGER NOT NULL DEFAULT 0,
                deleted INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE TABLE merchant_rules (merchant TEXT PRIMARY KEY, category_id INTEGER NOT NULL)")
        DEFAULT_CATEGORIES.forEachIndexed { i, (key, emoji, color) ->
            db.insert("categories", null, ContentValues().apply {
                put("key", key); put("emoji", emoji); put("color", color); put("sort", i)
            })
        }
    }

    fun categoryIdsByKey(db: SQLiteDatabase): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        db.rawQuery("SELECT key, id FROM categories WHERE key IS NOT NULL", null).use { c ->
            while (c.moveToNext()) map[c.getString(0)] = c.getLong(1)
        }
        return map
    }
}

private fun Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)

/**
 * Єдине сховище для UI і NotificationListener (обидва в одному процесі).
 * Усі записи в БД — на одному фоновому потоці; UI читає StateFlow.
 */
object Repo {
    private lateinit var db: Db
    private lateinit var appContext: Context
    private val io = Executors.newSingleThreadExecutor()

    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    /** Вікно, у межах якого однакова сума вважається тією самою покупкою (гаманець + банк). */
    private const val DEDUPE_WINDOW_MS = 3 * 60 * 1000L

    @Synchronized
    fun init(context: Context) {
        if (::db.isInitialized) return
        appContext = context.applicationContext
        db = Db(appContext)
        io.execute { reloadAll() }
    }

    // ---------- Покупки ----------

    fun addManual(amountMinor: Long, currency: String, merchant: String?, categoryId: Long?, source: String, note: String? = null) {
        io.execute {
            val cat = categoryId ?: classify(merchant)
            insert(amountMinor, currency, merchant, cat, null, source, SourceType.MANUAL, System.currentTimeMillis(), note)
            afterChange(currency, cat, amountMinor)
        }
    }

    /**
     * Автоматичний запис зі сповіщення. Якщо за останні 3 хвилини вже є покупка
     * з тією ж сумою й валютою — це той самий платіж з іншого джерела:
     * доповнюємо запис (магазин/картка від банку, позначка NFC від гаманця), а не дублюємо.
     */
    fun addAuto(p: ParsedPayment, source: String) {
        io.execute {
            val now = System.currentTimeMillis()
            val dup = _purchases.value.firstOrNull {
                it.amountMinor == p.amountMinor && it.currency == p.currency &&
                    now - it.timestamp in 0..DEDUPE_WINDOW_MS && it.sourceType != SourceType.MANUAL
            }
            if (dup != null) {
                if (dup.source == source && dup.merchant == p.merchant) return@execute // те саме сповіщення вдруге
                val merchant = dup.merchant ?: p.merchant
                val values = ContentValues().apply {
                    put("merchant", merchant)
                    put("card", dup.card ?: p.card)
                    if (p.sourceType == SourceType.NFC) put("source_type", SourceType.NFC.name)
                    // Банк точніший за гаманець — показуємо його назву.
                    if (dup.sourceType == SourceType.NFC && p.sourceType != SourceType.NFC) put("source", source)
                    if (dup.merchant == null && merchant != null) put("category_id", classify(merchant))
                }
                db.writableDatabase.update("purchases", values, "id = ?", arrayOf(dup.id.toString()))
                reloadPurchases()
                return@execute
            }
            val cat = classify(p.merchant)
            insert(p.amountMinor, p.currency, p.merchant, cat, p.card, source, p.sourceType, now, null)
            afterChange(p.currency, cat, p.amountMinor)
        }
    }

    /**
     * Зберегти зміни. Якщо змінилась категорія і є магазин — запам'ятовуємо правило,
     * і наступні покупки в цьому магазині отримають ту ж категорію.
     */
    fun update(p: Purchase) {
        io.execute {
            val old = _purchases.value.firstOrNull { it.id == p.id }
            db.writableDatabase.update("purchases", ContentValues().apply {
                put("amount_minor", p.amountMinor)
                put("currency", p.currency)
                put("merchant", p.merchant)
                put("category_id", p.categoryId)
                put("note", p.note)
                put("ts", p.timestamp)
            }, "id = ?", arrayOf(p.id.toString()))
            if (old != null && old.categoryId != p.categoryId && !p.merchant.isNullOrBlank()) {
                learn(p.merchant, p.categoryId)
            }
            reloadPurchases()
        }
    }

    fun delete(id: Long) {
        io.execute {
            db.writableDatabase.delete("purchases", "id = ?", arrayOf(id.toString()))
            reloadPurchases()
        }
    }

    /** Повернути видалену покупку (кнопка «Скасувати»). */
    fun restore(p: Purchase) {
        io.execute {
            db.writableDatabase.insert("purchases", null, p.toValues().apply { put("id", p.id) })
            reloadPurchases()
        }
    }

    // ---------- Категорії та бюджети ----------

    fun addCategory(name: String, emoji: String, color: Long, budgetMinor: Long = 0) {
        io.execute {
            db.writableDatabase.insert("categories", null, ContentValues().apply {
                put("name", name); put("emoji", emoji); put("color", color); put("budget_minor", budgetMinor)
                put("sort", _categories.value.size)
            })
            reloadCategories()
        }
    }

    fun updateCategory(c: Category) {
        io.execute {
            db.writableDatabase.update("categories", ContentValues().apply {
                put("name", c.name); put("emoji", c.emoji); put("color", c.color); put("budget_minor", c.budgetMinor)
            }, "id = ?", arrayOf(c.id.toString()))
            reloadCategories()
        }
    }

    /** Видаляє категорію; її покупки переходять до «Інше». Вбудовану «Інше» видалити не можна. */
    fun deleteCategory(c: Category) {
        if (c.key == CategoryKeys.OTHER) return
        io.execute {
            val other = otherId()
            val w = db.writableDatabase
            w.execSQL("UPDATE purchases SET category_id = ? WHERE category_id = ?", arrayOf(other, c.id))
            w.execSQL("UPDATE merchant_rules SET category_id = ? WHERE category_id = ?", arrayOf(other, c.id))
            w.execSQL("UPDATE categories SET deleted = 1 WHERE id = ?", arrayOf(c.id))
            reloadAll()
        }
    }

    fun categoryById(id: Long): Category? = _categories.value.firstOrNull { it.id == id }

    // ---------- Внутрішнє ----------

    private fun otherId(): Long = db.categoryIdsByKey(db.readableDatabase)[CategoryKeys.OTHER] ?: 0L

    private fun learnedRules(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        db.readableDatabase.rawQuery("SELECT merchant, category_id FROM merchant_rules", null).use { c ->
            while (c.moveToNext()) map[c.getString(0)] = c.getLong(1).toString()
        }
        return map
    }

    private fun classify(merchant: String?): Long {
        val result = CategoryClassifier.classify(merchant, learnedRules())
        result.toLongOrNull()?.let { id -> if (_categories.value.any { it.id == id }) return id }
        return db.categoryIdsByKey(db.readableDatabase)[result] ?: otherId()
    }

    private fun learn(merchant: String, categoryId: Long) {
        db.writableDatabase.insertWithOnConflict(
            "merchant_rules", null,
            ContentValues().apply { put("merchant", CategoryClassifier.normalize(merchant)); put("category_id", categoryId) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun insert(
        amountMinor: Long, currency: String, merchant: String?, categoryId: Long, card: String?,
        source: String, sourceType: SourceType, ts: Long, note: String?,
    ) {
        db.writableDatabase.insert("purchases", null, ContentValues().apply {
            put("amount_minor", amountMinor); put("currency", currency); put("merchant", merchant)
            put("category_id", categoryId); put("card", card); put("source", source)
            put("source_type", sourceType.name); put("ts", ts); put("note", note)
        })
    }

    private fun Purchase.toValues() = ContentValues().apply {
        put("amount_minor", amountMinor); put("currency", currency); put("merchant", merchant)
        put("category_id", categoryId); put("card", card); put("source", source)
        put("source_type", sourceType.name); put("ts", timestamp); put("note", note)
    }

    /** Після нової витрати: оновити дані й перевірити пороги бюджетів. */
    private fun afterChange(currency: String, categoryId: Long, amountMinor: Long) {
        val before = _purchases.value
        reloadPurchases()
        if (currency != AppSettings.baseCurrency(appContext)) return
        val month = Stats.budgetMonth(System.currentTimeMillis(), AppSettings.monthStartDay(appContext))
        val monthBefore = Stats.inRange(before, month, currency)
        val spentBefore = Stats.total(monthBefore)
        val spentAfter = spentBefore + amountMinor
        BudgetNotifier.check(appContext, null, spentBefore, spentAfter, AppSettings.monthlyBudget(appContext))
        val cat = categoryById(categoryId) ?: return
        val catBefore = Stats.total(monthBefore.filter { it.categoryId == categoryId })
        BudgetNotifier.check(appContext, cat, catBefore, catBefore + amountMinor, cat.budgetMinor)
    }

    private fun reloadAll() {
        reloadCategories()
        reloadPurchases()
    }

    private fun reloadCategories() {
        val list = mutableListOf<Category>()
        db.readableDatabase.rawQuery(
            "SELECT id, key, name, emoji, color, budget_minor FROM categories WHERE deleted = 0 ORDER BY sort, id", null,
        ).use { c ->
            while (c.moveToNext()) {
                list += Category(c.getLong(0), c.getStringOrNull(1), c.getStringOrNull(2), c.getString(3), c.getLong(4), c.getLong(5))
            }
        }
        _categories.value = list
    }

    private fun reloadPurchases() {
        val list = mutableListOf<Purchase>()
        db.readableDatabase.rawQuery(
            "SELECT id, amount_minor, currency, merchant, category_id, card, source, source_type, ts, note " +
                "FROM purchases ORDER BY ts DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                list += Purchase(
                    id = c.getLong(0),
                    amountMinor = c.getLong(1),
                    currency = c.getString(2),
                    merchant = c.getStringOrNull(3),
                    categoryId = c.getLong(4),
                    card = c.getStringOrNull(5),
                    source = c.getString(6),
                    sourceType = runCatching { SourceType.valueOf(c.getString(7)) }.getOrDefault(SourceType.CARD),
                    timestamp = c.getLong(8),
                    note = c.getStringOrNull(9),
                )
            }
        }
        _purchases.value = list
    }

    fun toCsv(list: List<Purchase>, categoryName: (Long) -> String): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT)
        fun esc(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
        val sb = StringBuilder("date;amount;currency;merchant;category;card;source;type;note\n")
        for (p in list) {
            sb.append(fmt.format(java.util.Date(p.timestamp))).append(';')
                .append("%d.%02d".format(p.amountMinor / 100, p.amountMinor % 100)).append(';')
                .append(p.currency).append(';').append(esc(p.merchant)).append(';')
                .append(esc(categoryName(p.categoryId))).append(';').append(p.card.orEmpty()).append(';')
                .append(esc(p.source)).append(';').append(p.sourceType.name).append(';')
                .append(esc(p.note)).append('\n')
        }
        return sb.toString()
    }
}

/** Налаштування в SharedPreferences. */
object AppSettings {
    private const val FILE = "settings"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun captureAllApps(ctx: Context) = prefs(ctx).getBoolean("capture_all", false)
    fun setCaptureAllApps(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("capture_all", v).apply()

    /** Вимкнені користувачем джерела (пакети). */
    fun disabledApps(ctx: Context): Set<String> = prefs(ctx).getStringSet("disabled_apps", emptySet()) ?: emptySet()
    fun setAppEnabled(ctx: Context, pkg: String, enabled: Boolean) {
        val set = disabledApps(ctx).toMutableSet()
        if (enabled) set.remove(pkg) else set.add(pkg)
        prefs(ctx).edit().putStringSet("disabled_apps", set).apply()
    }

    fun baseCurrency(ctx: Context) = prefs(ctx).getString("base_currency", "UAH") ?: "UAH"
    fun setBaseCurrency(ctx: Context, v: String) = prefs(ctx).edit().putString("base_currency", v).apply()

    fun monthlyBudget(ctx: Context) = prefs(ctx).getLong("monthly_budget", 0L)
    fun setMonthlyBudget(ctx: Context, v: Long) = prefs(ctx).edit().putLong("monthly_budget", v).apply()

    fun monthStartDay(ctx: Context) = prefs(ctx).getInt("month_start", 1)
    fun setMonthStartDay(ctx: Context, v: Int) = prefs(ctx).edit().putInt("month_start", v.coerceIn(1, 28)).apply()

    fun onboardingDone(ctx: Context) = prefs(ctx).getBoolean("onboarding_done", false)
    fun setOnboardingDone(ctx: Context) = prefs(ctx).edit().putBoolean("onboarding_done", true).apply()

    fun tagMerchant(ctx: Context, tagId: String): String? = prefs(ctx).getString("tag_$tagId", null)
    fun setTagMerchant(ctx: Context, tagId: String, merchant: String) =
        prefs(ctx).edit().putString("tag_$tagId", merchant).apply()
}
