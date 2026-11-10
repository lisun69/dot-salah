package com.abidur.dotsalah

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

val PRAYERS = listOf("FAJR", "DHUHR", "ASR", "MAGHRIB", "ISHA")
val ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

data class City(val name: String, val lat: Double, val lng: Double)

val CITIES = listOf(
    City("DHAKA", 23.8103, 90.4125),
    City("GAZIPUR", 23.9999, 90.4203),
    City("NARAYANGANJ", 23.6238, 90.4990),
    City("CHATTOGRAM", 22.3569, 91.7832),
    City("COX'S BAZAR", 21.4272, 92.0058),
    City("CUMILLA", 23.4607, 91.1809),
    City("SYLHET", 24.8949, 91.8687),
    City("MYMENSINGH", 24.7471, 90.4203),
    City("RAJSHAHI", 24.3745, 88.6042),
    City("RANGPUR", 25.7439, 89.2752),
    City("KHULNA", 22.8456, 89.5403),
    City("BARISHAL", 22.7010, 90.3535)
)

/**
 * Astronomical prayer times. Method: University of Islamic Sciences, Karachi
 * (Fajr 18 deg, Isha 18 deg), as used in Bangladesh. Asr: Hanafi (default) or Standard.
 */
object PrayerCalc {
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun fixAngle(a: Double) = ((a % 360) + 360) % 360
    private fun fixHour(h: Double) = ((h % 24) + 24) % 24

    private fun julian(y0: Int, m0: Int, d: Int): Double {
        var y = y0
        var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    /** returns [declination, equationOfTime] */
    private fun sun(jd: Double): DoubleArray {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)))
        val e = 23.439 - 0.00000036 * d
        val ra = deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15
        val eqt = q / 15 - fixHour(ra)
        val decl = deg(asin(sin(rad(e)) * sin(rad(l))))
        return doubleArrayOf(decl, eqt)
    }

    /** Hours (local, UTC+6) for fajr, sunrise, dhuhr, asr, maghrib, isha. */
    fun hours(date: LocalDate, lat: Double, lng: Double, hanafi: Boolean): DoubleArray {
        val jd = julian(date.year, date.monthValue, date.dayOfMonth) - lng / (15 * 24)
        fun mid(t: Double) = fixHour(12 - sun(jd + t)[1])
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sun(jd + t)[0]
            val noon = mid(t)
            val v = (-sin(rad(angle)) - sin(rad(decl)) * sin(rad(lat))) / (cos(rad(decl)) * cos(rad(lat)))
            val tt = deg(acos(v.coerceIn(-1.0, 1.0))) / 15
            return if (ccw) noon - tt else noon + tt
        }
        fun asr(t: Double): Double {
            val decl = sun(jd + t)[0]
            val factor = if (hanafi) 2.0 else 1.0
            val angle = -deg(atan(1 / (factor + tan(rad(abs(lat - decl))))))
            return angleTime(angle, t, false)
        }
        val raw = doubleArrayOf(
            angleTime(18.0, 5 / 24.0, true),
            angleTime(0.833, 6 / 24.0, true),
            mid(12 / 24.0),
            asr(13 / 24.0),
            angleTime(0.833, 18 / 24.0, false),
            angleTime(18.0, 18 / 24.0, false)
        )
        return DoubleArray(6) { raw[it] + 6.0 - lng / 15 }
    }
}

/** One day's times as epoch millis. prayers[0..4] = Fajr..Isha. */
class DayTimes(val date: LocalDate, val prayers: LongArray, val sunrise: Long)

object Settings {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("salah_settings", Context.MODE_PRIVATE)

    /** Default minute offsets so times match the Islamic Foundation Bangladesh timetable. */
    val DEFAULT_ADJ = intArrayOf(2, 3, 0, 3, 2)

    fun cityIndex(c: Context) = p(c).getInt("city", 0).coerceIn(0, CITIES.lastIndex)
    fun city(c: Context) = CITIES[cityIndex(c)]
    fun setCity(c: Context, i: Int) = p(c).edit().putInt("city", i).apply()

    fun use24(c: Context) = p(c).getBoolean("use24", false)
    fun setUse24(c: Context, v: Boolean) = p(c).edit().putBoolean("use24", v).apply()

    fun hanafi(c: Context) = p(c).getBoolean("hanafi", true)
    fun setHanafi(c: Context, v: Boolean) = p(c).edit().putBoolean("hanafi", v).apply()

    fun adj(c: Context, i: Int) = p(c).getInt("adj$i", DEFAULT_ADJ[i])
    fun setAdj(c: Context, i: Int, v: Int) = p(c).edit().putInt("adj$i", v.coerceIn(-30, 30)).apply()
    fun resetAdj(c: Context) = p(c).edit().apply { for (i in 0..4) remove("adj$i") }.apply()

    fun notify(c: Context) = p(c).getBoolean("notify", true)
    fun setNotify(c: Context, v: Boolean) = p(c).edit().putBoolean("notify", v).apply()

    fun vibrate(c: Context) = p(c).getBoolean("vibrate", true)
    fun setVibrate(c: Context, v: Boolean) = p(c).edit().putBoolean("vibrate", v).apply()

    fun lastNotified(c: Context) = p(c).getLong("lastNotified", 0L)
    fun setLastNotified(c: Context, v: Long) = p(c).edit().putLong("lastNotified", v).apply()
}

object Salah {
    fun today(): LocalDate = LocalDate.now(ZONE)

    fun times(c: Context, date: LocalDate): DayTimes {
        val city = Settings.city(c)
        val h = PrayerCalc.hours(date, city.lat, city.lng, Settings.hanafi(c))
        val base = date.atStartOfDay(ZONE).toInstant().toEpochMilli()
        fun ms(hours: Double, adjMin: Int): Long {
            val minutes = (hours * 60).roundToLong() + adjMin
            return base + minutes * 60_000L
        }
        val idx = intArrayOf(0, 2, 3, 4, 5)
        val prayers = LongArray(5) { ms(h[idx[it]], Settings.adj(c, it)) }
        return DayTimes(date, prayers, ms(h[1], 0))
    }

    /** Next prayer strictly after [now]: (index, time, isTomorrow). */
    fun next(c: Context, now: Long = System.currentTimeMillis()): Triple<Int, Long, Boolean> {
        val t = times(c, Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate())
        for (i in 0..4) if (t.prayers[i] > now) return Triple(i, t.prayers[i], false)
        val tm = times(c, t.date.plusDays(1))
        return Triple(0, tm.prayers[0], true)
    }

    fun nextMidnight(): Long = today().plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli()

    // ---------- tracking ----------
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("salah_log", Context.MODE_PRIVATE)
    private fun key(d: LocalDate) = "d_$d"

    fun mask(c: Context, d: LocalDate) = p(c).getInt(key(d), 0)
    fun isDone(c: Context, d: LocalDate, i: Int) = mask(c, d) and (1 shl i) != 0
    fun count(c: Context, d: LocalDate) = Integer.bitCount(mask(c, d))

    fun set(c: Context, d: LocalDate, i: Int, done: Boolean) {
        val m = mask(c, d)
        val n = if (done) m or (1 shl i) else m and (1 shl i).inv()
        p(c).edit().putInt(key(d), n).apply()
    }

    fun toggle(c: Context, d: LocalDate, i: Int) = set(c, d, i, !isDone(c, d, i))

    /** Full days (5/5) and days counted, for this month up to today. */
    fun monthFull(c: Context, month: LocalDate = today()): Pair<Int, Int> {
        val first = month.withDayOfMonth(1)
        val last = if (first.year == today().year && first.month == today().month) today()
        else first.withDayOfMonth(first.lengthOfMonth())
        if (first.isAfter(today())) return 0 to 0
        var full = 0
        var days = 0
        var d = first
        while (!d.isAfter(last)) {
            days++
            if (count(c, d) == 5) full++
            d = d.plusDays(1)
        }
        return full to days
    }

    fun streak(c: Context): Int {
        var d = today()
        if (count(c, d) < 5) d = d.minusDays(1)
        var s = 0
        while (count(c, d) == 5 && s < 3650) { s++; d = d.minusDays(1) }
        return s
    }
}

object Fmt {
    private val f24 = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val f12 = DateTimeFormatter.ofPattern("h:mm", Locale.US)
    private val ampm = DateTimeFormatter.ofPattern("a", Locale.US)

    private fun z(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZONE)

    /** "15:39" or "3:39 PM" */
    fun time(c: Context, ms: Long): String =
        if (Settings.use24(c)) z(ms).format(f24) else z(ms).format(f12) + " " + z(ms).format(ampm).uppercase(Locale.US)

    /** Number part only, for big dot display. */
    fun timeShort(c: Context, ms: Long): String = if (Settings.use24(c)) z(ms).format(f24) else z(ms).format(f12)
    fun suffix(c: Context, ms: Long): String = if (Settings.use24(c)) "" else z(ms).format(ampm).uppercase(Locale.US)

    fun date(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE dd MMM", Locale.US)).uppercase(Locale.US)
    fun monthTitle(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)).uppercase(Locale.US)

    fun countdown(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%dH %02dM", h, m) else String.format(Locale.US, "%dM %02dS", m, sec)
    }

    fun title(name: String) = name.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }

    @Suppress("unused")
    fun localTime(ms: Long): LocalTime = z(ms).toLocalTime()
}
