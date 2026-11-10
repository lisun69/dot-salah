package com.abidur.dotsalah

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import kotlin.math.abs

const val ACTION_PRAYER = "com.abidur.dotsalah.PRAYER"
const val ACTION_MIDNIGHT = "com.abidur.dotsalah.MIDNIGHT"
const val ACTION_TOGGLE = "com.abidur.dotsalah.TOGGLE"
const val ACTION_MARK = "com.abidur.dotsalah.MARK"

/** Sets the next alarm (next prayer) and a midnight refresh. */
object Scheduler {
    fun schedule(c: Context) {
        val ctx = c.applicationContext
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val (idx, at, _) = Salah.next(ctx)

        val prayerIntent = Intent(ctx, SalahReceiver::class.java)
            .setAction(ACTION_PRAYER)
            .putExtra("idx", idx)
            .putExtra("at", at)
        set(am, at, PendingIntent.getBroadcast(ctx, 100, prayerIntent, flags))

        val midIntent = Intent(ctx, SalahReceiver::class.java).setAction(ACTION_MIDNIGHT)
        set(am, Salah.nextMidnight() + 5_000L, PendingIntent.getBroadcast(ctx, 101, midIntent, flags))
    }

    private fun set(am: AlarmManager, at: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

object Notifier {
    private const val CH_VIBRATE = "salah_vibrate"
    private const val CH_QUIET = "salah_no_vibrate"

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        val v = NotificationChannel(CH_VIBRATE, "Prayer time (vibrate)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminder at each prayer time"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
        }
        val q = NotificationChannel(CH_QUIET, "Prayer time (no vibration)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminder at each prayer time without vibration"
            enableVibration(false)
        }
        nm.createNotificationChannel(v)
        nm.createNotificationChannel(q)
    }

    fun canPost(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(c: Context, idx: Int, at: Long) {
        if (!canPost(c)) return
        channels(c)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            c, 200, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags
        )
        val mark = PendingIntent.getBroadcast(
            c, 300 + idx,
            Intent(c, SalahReceiver::class.java).setAction(ACTION_MARK)
                .putExtra("idx", idx).putExtra("date", Salah.today().toString()),
            flags
        )
        val name = Fmt.title(PRAYERS[idx])
        val n = NotificationCompat.Builder(c, if (Settings.vibrate(c)) CH_VIBRATE else CH_QUIET)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFD71921.toInt())
            .setContentTitle("$name  ${Fmt.time(c, at)}")
            .setContentText("It's time for $name in ${Fmt.title(Settings.city(c).name)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Mark $name as prayed", mark)
            .build()
        try {
            NotificationManagerCompat.from(c).notify(1000 + idx, n)
        } catch (e: SecurityException) {
            // permission removed
        }
    }

    fun test(c: Context) = show(c, Salah.next(c).first, Salah.next(c).second)
}

/** Handles alarms, widget taps and the notification button. */
class SalahReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.applicationContext
        when (intent.action) {
            ACTION_PRAYER -> {
                val idx = intent.getIntExtra("idx", -1)
                val at = intent.getLongExtra("at", 0L)
                val now = System.currentTimeMillis()
                if (idx in 0..4 && abs(now - at) < 15 * 60_000L && Settings.lastNotified(c) != at) {
                    if (now >= at - 90_000L) {
                        Settings.setLastNotified(c, at)
                        if (Settings.notify(c)) Notifier.show(c, idx, at)
                    }
                }
            }
            ACTION_TOGGLE -> {
                val idx = intent.getIntExtra("idx", -1)
                if (idx in 0..4) {
                    val today = Salah.today()
                    val t = Salah.times(c, today)
                    if (!Salah.isDone(c, today, idx) && System.currentTimeMillis() < t.prayers[idx]) {
                        Toast.makeText(c, "${Fmt.title(PRAYERS[idx])} starts at ${Fmt.time(c, t.prayers[idx])}", Toast.LENGTH_SHORT).show()
                    } else {
                        Salah.toggle(c, today, idx)
                    }
                }
            }
            ACTION_MARK -> {
                val idx = intent.getIntExtra("idx", -1)
                val date = runCatching { LocalDate.parse(intent.getStringExtra("date")) }.getOrNull() ?: Salah.today()
                if (idx in 0..4) {
                    Salah.set(c, date, idx, true)
                    NotificationManagerCompat.from(c).cancel(1000 + idx)
                    Toast.makeText(c, "${Fmt.title(PRAYERS[idx])} marked", Toast.LENGTH_SHORT).show()
                }
            }
        }
        SalahWidget.refresh(c)
        Scheduler.schedule(c)
    }
}

/** Re-sets alarms after restart, update or clock change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.applicationContext
        Scheduler.schedule(c)
        SalahWidget.refresh(c)
    }
}
