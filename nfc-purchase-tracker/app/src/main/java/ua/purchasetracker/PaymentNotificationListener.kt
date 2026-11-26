package ua.purchasetracker

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Читає сповіщення Samsung Wallet / Google Wallet / банків, які з'являються після оплати,
 * і автоматично записує покупку.
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (pkg in AppSettings.disabledApps(this)) return
        val known = pkg in PaymentParser.KNOWN_PAYMENT_APPS
        if (!known && !AppSettings.captureAllApps(this)) return

        val n = sbn.notification ?: return
        // Групові «підсумкові» сповіщення дублюють дочірні.
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

        val label = appLabel(pkg)
        val parsed = PaymentParser.parse(pkg, title, text, label) ?: return
        Repo.addAuto(parsed, label)
    }

    private fun appLabel(pkg: String): String =
        PaymentParser.KNOWN_PAYMENT_APPS[pkg] ?: try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
}
