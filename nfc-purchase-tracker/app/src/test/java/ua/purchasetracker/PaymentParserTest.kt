package ua.purchasetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentParserTest {

    private val wallet = "com.samsung.android.spay"
    private val mono = "com.ftband.mono"
    private val privat = "ua.privatbank.ap24"

    @Test fun samsungWalletUah() {
        val p = PaymentParser.parse(wallet, "Samsung Wallet", "Оплата ₴245,50 в АТБ-Маркет")!!
        assertEquals(24550L, p.amountMinor)
        assertEquals("UAH", p.currency)
    }

    @Test fun samsungWalletEnglishMerchant() {
        val p = PaymentParser.parse(wallet, "Samsung Wallet", "Payment of $12.99 at Starbucks")!!
        assertEquals(1299L, p.amountMinor)
        assertEquals("USD", p.currency)
        assertEquals("Starbucks", p.merchant)
    }

    @Test fun monoMinusAmountTitleIsMerchant() {
        val p = PaymentParser.parse(mono, "Сільпо", "−1 245.50₴\nБаланс 10 000.00₴")!!
        assertEquals(124550L, p.amountMinor)
        assertEquals("Сільпо", p.merchant)
    }

    @Test fun privatDebit() {
        val p = PaymentParser.parse(privat, "Приват24", "Списання 89.00 UAH Картка 5168**1234. Novus. Бал. 1500.00UAH")!!
        assertEquals(8900L, p.amountMinor)
        assertEquals("UAH", p.currency)
        assertNull(p.merchant)
    }

    @Test fun skipsBalanceAmount() {
        val p = PaymentParser.parse(privat, null, "Баланс: 5 000,00 грн. Покупка 120 грн")!!
        assertEquals(12000L, p.amountMinor)
    }

    @Test fun ignoresIncome() {
        assertNull(PaymentParser.parse(mono, "Поповнення", "+500.00₴ від Іван"))
        assertNull(PaymentParser.parse(privat, null, "Зарахування 1000.00 UAH"))
    }

    @Test fun ignoresPromoWithoutKeyword() {
        assertNull(PaymentParser.parse(wallet, "Samsung Wallet", "Знижки до 300 ₴ у партнерів"))
    }

    @Test fun usFormatThousands() {
        val p = PaymentParser.parse(wallet, null, "Purchase €1,234.50 at IKEA")!!
        assertEquals(123450L, p.amountMinor)
        assertEquals("EUR", p.currency)
        assertEquals("IKEA", p.merchant)
    }

    @Test fun toMinorVariants() {
        assertEquals(4500L, PaymentParser.toMinor("45"))
        assertEquals(4550L, PaymentParser.toMinor("45,5"))
        assertEquals(123456L, PaymentParser.toMinor("1.234,56"))
        assertEquals(123400L, PaymentParser.toMinor("1 234"))
    }

    @Test fun formatting() {
        assertEquals("1 234,56 ₴", PaymentParser.format(123456, "UAH"))
        assertEquals("0,05 $", PaymentParser.format(5, "USD"))
    }

    @Test fun cardAndSourceType() {
        val bank = PaymentParser.parse(privat, null, "Списання 89.00 UAH Картка 5168**1234. Novus")!!
        assertEquals("1234", bank.card)
        assertEquals(SourceType.CARD, bank.sourceType)
        val wallet = PaymentParser.parse(wallet, "Samsung Wallet", "Оплата ₴45,00 картка 7788")!!
        assertEquals("7788", wallet.card)
        assertEquals(SourceType.NFC, wallet.sourceType)
        val online = PaymentParser.parse(mono, "Rozetka", "Онлайн-покупка 1 299.00₴")!!
        assertEquals(SourceType.ONLINE, online.sourceType)
    }

    @Test fun polishBank() {
        val p = PaymentParser.parse("pl.pkobp.iko", "IKO", "Płatność kartą 45,99 PLN Biedronka. Saldo: 1 200,00 PLN")!!
        assertEquals(4599L, p.amountMinor)
        assertEquals("PLN", p.currency)
        assertNull(PaymentParser.parse("pl.mbank", "mBank", "Wpływ 500,00 PLN"))
    }
}
