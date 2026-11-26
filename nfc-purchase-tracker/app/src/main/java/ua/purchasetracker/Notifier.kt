package ua.purchasetracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Локалізована назва категорії (вбудовані — з ресурсів, власні — як ввів користувач). */
fun categoryLabel(ctx: Context, c: Category?): String {
    if (c == null) return ctx.getString(R.string.cat_other)
    c.name?.let { return it }
    val res = when (c.key) {
        CategoryKeys.GROCERIES -> R.string.cat_groceries
        CategoryKeys.CAFE -> R.string.cat_cafe
        CategoryKeys.TRANSPORT -> R.string.cat_transport
        CategoryKeys.FUEL -> R.string.cat_fuel
        CategoryKeys.HEALTH -> R.string.cat_health
        CategoryKeys.CLOTHES -> R.string.cat_clothes
        CategoryKeys.HOME -> R.string.cat_home
        CategoryKeys.ENTERTAINMENT -> R.string.cat_entertainment
        CategoryKeys.SUBSCRIPTIONS -> R.string.cat_subscriptions
        CategoryKeys.GIFTS -> R.string.cat_gifts
        else -> R.string.cat_other
    }
    return ctx.getString(res)
}

/** Сповіщення про 80 % і 100 % бюджету. */
object BudgetNotifier {
    private const val CHANNEL = "budget"

    fun check(ctx: Context, category: Category?, before: Long, after: Long, budget: Long) {
        val percent = when {
            Stats.crossed(before, after, budget, 100) -> 100
            Stats.crossed(before, after, budget, 80) -> 80
            else -> return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.channel_budget), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val what = category?.let { "${it.emoji} ${categoryLabel(ctx, it)}" } ?: ctx.getString(R.string.budget_total)
        val currency = AppSettings.baseCurrency(ctx)
        val title = if (percent >= 100) ctx.getString(R.string.budget_exceeded, what)
        else ctx.getString(R.string.budget_80, what)
        val text = ctx.getString(
            R.string.budget_spent_of,
            Money.format(after, currency),
            Money.format(budget, currency),
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tapsum)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify((category?.id ?: 0L).toInt() * 10 + percent / 10, n)
        } catch (_: SecurityException) {
        }
    }
}
