package com.abidur.dotsalah

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import java.util.Locale

/** Resizable widget: next prayer on the left, today's 5 times + ticks on the right, 2 progress bars. */
class SalahWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { draw(context, appWidgetManager, it) }
        Scheduler.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        draw(context, appWidgetManager, appWidgetId)
    }

    companion object {
        private val RED = 0xFFD71921.toInt()

        private val ROWS = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4)
        private val NAMES = intArrayOf(R.id.name0, R.id.name1, R.id.name2, R.id.name3, R.id.name4)
        private val TIMES = intArrayOf(R.id.time0, R.id.time1, R.id.time2, R.id.time3, R.id.time4)
        private val TICKS = intArrayOf(R.id.tick0, R.id.tick1, R.id.tick2, R.id.tick3, R.id.tick4)

        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            mgr.getAppWidgetIds(ComponentName(context, SalahWidget::class.java)).forEach { draw(context, mgr, it) }
        }

        private fun draw(c: Context, mgr: AppWidgetManager, id: Int) {
            val opts = mgr.getAppWidgetOptions(id)
            val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 180
            val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 300

            val st = Settings.widgetStyle(c)
            val pri = st.primaryInt
            val sec = st.secondaryInt
            val ghost = st.ghostInt

            val now = System.currentTimeMillis()
            val today = Salah.today()
            val t = Salah.times(c, today)
            val (nextIdx, nextAt, tomorrow) = Salah.next(c, now)
            val v = RemoteViews(c.packageName, R.layout.widget_salah)

            // ---- theme + opacity ----
            v.setImageViewResource(R.id.bg, st.bgRes)
            v.setInt(R.id.bg, "setImageAlpha", Settings.widgetOpacity(c) * 255 / 100)
            v.setInt(R.id.divider, "setBackgroundColor", st.dividerInt)
            v.setInt(R.id.settings_btn, "setColorFilter", sec)
            intArrayOf(R.id.location, R.id.next_label, R.id.label_today, R.id.label_month).forEach { v.setTextColor(it, sec) }
            intArrayOf(R.id.next_time, R.id.pct_today, R.id.pct_month).forEach { v.setTextColor(it, pri) }

            // ---- left: location + next prayer ----
            v.setTextViewText(R.id.location, Settings.city(c).name)
            v.setTextViewText(R.id.next_label, if (tomorrow) "NEXT · TOMORROW" else "NEXT")
            v.setImageViewBitmap(R.id.next_name, DotFont.bitmap(PRAYERS[nextIdx], 12, pri, ghost))
            v.setTextViewText(R.id.next_time, Fmt.time(c, nextAt))
            val base = SystemClock.elapsedRealtime() + (nextAt - now)
            v.setChronometer(R.id.countdown, base, "IN %s", true)
            v.setChronometerCountDown(R.id.countdown, true)

            // ---- Ramadan line (only during Ramadan) ----
            val rDay = Ramadan.dayNumber(c, today)
            if (rDay != null) {
                v.setTextViewText(R.id.ramadan_line, "SEHRI ${Fmt.time(c, t.sehri)} · IFTAR ${Fmt.time(c, t.iftar)}")
                v.setViewVisibility(R.id.ramadan_line, if (h < 120) View.GONE else View.VISIBLE)
            } else {
                v.setViewVisibility(R.id.ramadan_line, View.GONE)
            }

            // ---- right: today's 5 prayers with ticks ----
            for (i in 0..4) {
                val done = Salah.isDone(c, today, i)
                val started = now >= t.prayers[i]
                val isNext = !tomorrow && i == nextIdx
                val col = if (isNext) RED else if (started) pri else sec
                v.setTextViewText(NAMES[i], PRAYERS[i])
                v.setTextViewText(TIMES[i], Fmt.time(c, t.prayers[i]))
                v.setTextColor(NAMES[i], col)
                v.setTextColor(TIMES[i], col)
                v.setImageViewResource(
                    TICKS[i],
                    when {
                        done -> R.drawable.tick_done
                        started -> if (st.light) R.drawable.tick_due_light else R.drawable.tick_due
                        else -> if (st.light) R.drawable.tick_future_light else R.drawable.tick_future
                    }
                )
                v.setOnClickPendingIntent(
                    ROWS[i],
                    PendingIntent.getBroadcast(
                        c, 400 + i,
                        Intent(c, SalahReceiver::class.java).setAction(ACTION_TOGGLE).putExtra("idx", i),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }

            // ---- bars ----
            val count = Salah.count(c, today)
            v.setImageViewBitmap(R.id.bar_today, DotFont.segmentBitmap(5, 4, count, RED, ghost, 14))
            v.setTextViewText(R.id.pct_today, "${count * 20}%")
            val (full, days) = Salah.monthFull(c)
            v.setImageViewBitmap(R.id.bar_month, DotFont.barBitmap(24, if (days > 0) full.toDouble() / days else 0.0, pri, ghost, 14))
            v.setTextViewText(R.id.pct_month, String.format(Locale.US, "%d/%d", full, days))

            // ---- size rules ----
            val narrow = w < 230
            v.setViewVisibility(R.id.right_col, if (narrow) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.divider, if (narrow) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.bars, if (h < 150) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.countdown, if (h < 90) View.GONE else View.VISIBLE)

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val open = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags
            )
            val settings = PendingIntent.getActivity(
                c, 2, Intent(c, WidgetSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK), flags
            )
            v.setOnClickPendingIntent(R.id.left_col, open)
            v.setOnClickPendingIntent(R.id.bars, open)
            v.setOnClickPendingIntent(R.id.settings_btn, settings)

            mgr.updateAppWidget(id, v)
        }
    }
}
