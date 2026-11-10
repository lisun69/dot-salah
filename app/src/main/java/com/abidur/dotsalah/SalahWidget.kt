package com.abidur.dotsalah

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
        private val GREY = 0xFF8A8A8A.toInt()
        private val GHOST = 0x26FFFFFF

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

            val now = System.currentTimeMillis()
            val today = Salah.today()
            val t = Salah.times(c, today)
            val (nextIdx, nextAt, tomorrow) = Salah.next(c, now)
            val v = RemoteViews(c.packageName, R.layout.widget_salah)

            // ---- left: location + next prayer ----
            v.setTextViewText(R.id.location, Settings.city(c).name)
            v.setTextViewText(R.id.next_label, if (tomorrow) "NEXT · TOMORROW" else "NEXT")
            v.setImageViewBitmap(R.id.next_name, DotFont.bitmap(PRAYERS[nextIdx], 12, Color.WHITE, GHOST))
            v.setTextViewText(R.id.next_time, Fmt.time(c, nextAt))
            val base = SystemClock.elapsedRealtime() + (nextAt - now)
            v.setChronometer(R.id.countdown, base, "IN %s", true)
            v.setChronometerCountDown(R.id.countdown, true)

            // ---- right: today's 5 prayers with ticks ----
            for (i in 0..4) {
                val done = Salah.isDone(c, today, i)
                val started = now >= t.prayers[i]
                v.setTextViewText(NAMES[i], PRAYERS[i])
                v.setTextViewText(TIMES[i], Fmt.time(c, t.prayers[i]))
                val isNext = !tomorrow && i == nextIdx
                v.setTextColor(NAMES[i], if (isNext) RED else if (started) Color.WHITE else GREY)
                v.setTextColor(TIMES[i], if (isNext) RED else if (started) Color.WHITE else GREY)
                v.setImageViewResource(
                    TICKS[i],
                    when {
                        done -> R.drawable.tick_done
                        started -> R.drawable.tick_due
                        else -> R.drawable.tick_future
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

            // ---- bars: today (20% per prayer) and month consistency ----
            val count = Salah.count(c, today)
            v.setImageViewBitmap(R.id.bar_today, DotFont.segmentBitmap(5, 4, count, RED, GHOST, 14))
            v.setTextViewText(R.id.pct_today, "${count * 20}%")
            val (full, days) = Salah.monthFull(c)
            v.setImageViewBitmap(
                R.id.bar_month,
                DotFont.barBitmap(24, if (days > 0) full.toDouble() / days else 0.0, Color.WHITE, GHOST, 14)
            )
            v.setTextViewText(R.id.pct_month, String.format(Locale.US, "%d/%d", full, days))

            // ---- size rules ----
            val narrow = w < 230
            v.setViewVisibility(R.id.right_col, if (narrow) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.divider, if (narrow) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.bars, if (h < 150) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.countdown, if (h < 90) View.GONE else View.VISIBLE)

            val open = PendingIntent.getActivity(
                c, 0,
                Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.left_col, open)
            v.setOnClickPendingIntent(R.id.bars, open)

            mgr.updateAppWidget(id, v)
        }
    }
}
