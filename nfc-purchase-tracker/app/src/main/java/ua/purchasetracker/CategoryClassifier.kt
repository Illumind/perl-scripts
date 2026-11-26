package ua.purchasetracker

/** Ключі вбудованих категорій. Назви локалізуються в ресурсах (cat_<key>). */
object CategoryKeys {
    const val GROCERIES = "groceries"
    const val CAFE = "cafe"
    const val TRANSPORT = "transport"
    const val FUEL = "fuel"
    const val HEALTH = "health"
    const val CLOTHES = "clothes"
    const val HOME = "home"
    const val ENTERTAINMENT = "entertainment"
    const val SUBSCRIPTIONS = "subscriptions"
    const val GIFTS = "gifts"
    const val OTHER = "other"

    val ALL = listOf(GROCERIES, CAFE, TRANSPORT, FUEL, HEALTH, CLOTHES, HOME, ENTERTAINMENT, SUBSCRIPTIONS, GIFTS, OTHER)
}

/**
 * Визначає категорію за назвою магазину.
 * Спочатку — правила, яких навчив користувач (магазин → категорія), потім вбудований словник.
 */
object CategoryClassifier {

    private val dictionary: Map<String, List<String>> = mapOf(
        CategoryKeys.GROCERIES to listOf(
            "атб", "atb", "сільпо", "silpo", "novus", "новус", "фора", "fora", "варус", "varus",
            "metro cash", "метро кеш", "ашан", "auchan", "eko", "еко маркет", "копійка", "наш край",
            "траш", "thrash", "fozzy", "фоззі", "велмарт", "spar", "спар", "lidl", "biedronka",
            "żabka", "zabka", "carrefour", "kaufland", "netto", "dino", "lewiatan", "stokrotka",
            "aldi", "tesco", "rewe", "billa", "market", "маркет", "супермаркет", "продукти",
        ),
        CategoryKeys.CAFE to listOf(
            "кава", "кав'ярн", "кофе", "coffee", "cafe", "кафе", "café", "ресторан", "restaurant", "mcdonald", "макдональдс",
            "kfc", "burger", "pizza", "піца", "суші", "sushi", "starbucks", "aroma kava", "львівська майстерня",
            "пузата хата", "glovo", "bolt food", "wolt", "uber eats", "pyszne", "costa", "bar", "бар", "kawiarnia",
            "piekarnia", "пекарня", "bakery",
        ),
        CategoryKeys.TRANSPORT to listOf(
            "uklon", "уклон", "bolt", "uber", "таксі", "taxi", "метрополітен", "metro", "укрзалізниця",
            "uz.gov", "ukrzaliznytsia", "pkp", "intercity", "jakdojade", "mpk", "ztm", "busfor", "flixbus",
            "автобус", "транспорт", "parking", "паркінг", "парковка", "bilet",
        ),
        CategoryKeys.FUEL to listOf(
            "wog", "окко", "okko", "upg", "socar", "сокар", "shell", "orlen", "bp", "amic", "авіас", "avias",
            "brsm", "брсм", "klo", "кло", "circle k", "moya", "lotos", "азс", "fuel", "paliwo",
        ),
        CategoryKeys.HEALTH to listOf(
            "аптек", "apteka", "pharm", "фармац", "подорожник", "анц", "9-1-1", "doz", "rossmann", "лікар",
            "клінік", "clinic", "медичн", "стоматолог", "synevo", "сінево", "діла", "dila", "лабораторі",
        ),
        CategoryKeys.CLOTHES to listOf(
            "zara", "h&m", "hm", "reserved", "lc waikiki", "bershka", "pull&bear", "stradivarius", "sinsay",
            "cropp", "house", "mohito", "intertop", "інтертоп", "ccc", "deichmann", "answear", "decathlon",
            "одяг", "взуття", "sport",
        ),
        CategoryKeys.HOME to listOf(
            "епіцентр", "epicentr", "нова лінія", "leroy", "ikea", "jysk", "castorama", "obi", "rozetka",
            "розетка", "comfy", "фокстрот", "foxtrot", "eldorado", "ельдорадо", "allo", "алло", "media expert",
            "rtv euro", "action", "pepco", "jysk", "господар", "комунал", "kyivstar", "київстар", "vodafone",
            "lifecell", "водафон", "play", "orange", "plus", "t-mobile",
        ),
        CategoryKeys.ENTERTAINMENT to listOf(
            "multiplex", "мультиплекс", "планета кіно", "cinema", "кіно", "кінотеатр", "kino", "театр", "концерт",
            "karabas", "карабас", "concert", "steam", "playstation", "xbox", "nintendo", "боулінг",
            "більярд", "квест", "museum", "музей",
        ),
        CategoryKeys.SUBSCRIPTIONS to listOf(
            "netflix", "spotify", "youtube", "google", "apple.com", "itunes", "icloud", "megogo", "мегого",
            "sweet.tv", "kyivstar tv", "chatgpt", "openai", "anthropic", "claude", "adobe", "microsoft",
            "disney", "hbo", "max.com", "patreon", "boosty",
        ),
        CategoryKeys.GIFTS to listOf(
            "квіти", "квітк", "flower", "kwiat", "подарун", "gift", "prezent", "іграшк", "toy", "smyk", "antoshka", "антошка",
        ),
    )

    /** Нормалізація для пошуку: нижній регістр, одинарні пробіли. */
    fun normalize(merchant: String): String =
        merchant.lowercase().replace(Regex("\\s+"), " ").trim()

    /**
     * @param learned правила користувача: нормалізований магазин → ключ/ID категорії
     * @return ключ категорії або ID з learned; OTHER, якщо не впізнали
     */
    fun classify(merchant: String?, learned: Map<String, String> = emptyMap()): String {
        if (merchant.isNullOrBlank()) return CategoryKeys.OTHER
        val norm = normalize(merchant)
        learned[norm]?.let { return it }
        val tokens = norm.split(Regex("[^\\p{L}\\p{N}&]+")).filter { it.isNotEmpty() }.toSet()
        for ((key, words) in dictionary) {
            // Короткі слова (≤ 4 символи) — лише як окреме слово, щоб "кло" не ловило "клопіт".
            if (words.any { w -> if (w.length <= 4) w in tokens else norm.contains(w) }) return key
        }
        return CategoryKeys.OTHER
    }
}
