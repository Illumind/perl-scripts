package ua.purchasetracker

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryClassifierTest {
    @Test fun groceries() {
        assertEquals(CategoryKeys.GROCERIES, CategoryClassifier.classify("АТБ-Маркет"))
        assertEquals(CategoryKeys.GROCERIES, CategoryClassifier.classify("Сільпо"))
        assertEquals(CategoryKeys.GROCERIES, CategoryClassifier.classify("BIEDRONKA 1234 WARSZAWA"))
    }

    @Test fun fuelAndTransport() {
        assertEquals(CategoryKeys.FUEL, CategoryClassifier.classify("WOG AZS 12"))
        assertEquals(CategoryKeys.FUEL, CategoryClassifier.classify("ОККО"))
        assertEquals(CategoryKeys.TRANSPORT, CategoryClassifier.classify("Uklon"))
    }

    @Test fun cafeBeforeTransportForBoltFood() {
        assertEquals(CategoryKeys.CAFE, CategoryClassifier.classify("Bolt Food"))
    }

    @Test fun unknownAndEmpty() {
        assertEquals(CategoryKeys.OTHER, CategoryClassifier.classify("ФОП Петренко"))
        assertEquals(CategoryKeys.OTHER, CategoryClassifier.classify(null))
    }

    @Test fun learnedRuleWins() {
        val learned = mapOf("атб-маркет" to "42")
        assertEquals("42", CategoryClassifier.classify("  АТБ-Маркет ", learned))
    }
}
