package ua.purchasetracker

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Слухає сповіщення Samsung Wallet / Google Wallet / банків, які з'являються
 * одразу після безконтактної (NFC) оплати телефоном чи годинником, і записує суму.
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        PurchaseRepository.init(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val known = pkg in PaymentParser.KNOWN_PAYMENT_APPS
        if (!known && !Prefs.captureAllApps(this)) return

        val n = sbn.notification ?: return
        // Підсумкові "групові" сповіщення дублюють дочірні — пропускаємо.
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

        val label = appLabel(pkg)
        val parsed = PaymentParser.parse(pkg, title, text, label) ?: return
        PurchaseRepository.addAuto(parsed, label)
    }

    private fun appLabel(pkg: String): String =
        PaymentParser.KNOWN_PAYMENT_APPS[pkg] ?: try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
}
