package com.abidur.dotsalah

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.channels(this)
        Scheduler.schedule(this)
        SalahWidget.refresh(this)
        setContent { NothingTheme { SalahApp(tick.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue = tick.intValue + 1 // reload after ticking from the widget
    }
}

@Composable
fun SalahApp(tick: Int) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val changed: () -> Unit = {
        version += 1
        SalahWidget.refresh(ctx)
        Scheduler.schedule(ctx)
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(ctx)) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(Modifier.fillMaxSize().background(NBlack)) {
        TopBar()
        Tabs(tab) { tab = it }
        Box(Modifier.weight(1f)) {
            val key = tick + version * 100000
            when (tab) {
                0 -> TodayTab(key, changed)
                1 -> MonthTab(key, changed)
                else -> SetupTab(key, changed)
            }
        }
    }
}

@Composable
fun TopBar() {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DotText("SALAH", 3.dp, NWhite)
        Spacer(Modifier.width(8.dp))
        RedDot(7.dp)
        Spacer(Modifier.weight(1f))
        NText(Settings.city(ctx).name + "  " + Fmt.date(Salah.today()), 11.sp, NGrey, spacing = 1.sp, maxLines = 1)
    }
}

@Composable
fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    val names = listOf("TODAY", "MONTH", "SETUP")
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        names.forEachIndexed { i, n ->
            Column(
                Modifier.weight(1f).clickable { onSelect(i) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NText(n, 12.sp, if (i == selected) NWhite else NGrey, spacing = 1.5.sp, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (i == selected) NRed else Color.Transparent))
            }
        }
    }
}

// ---------------------------------------------------------------- TODAY
@Composable
fun TodayTab(key: Int, changed: () -> Unit) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val today = Salah.today()
    val t = remember(key, today) { Salah.times(ctx, today) }
    var local by remember { mutableIntStateOf(0) }
    val mask = remember(key, local, today) { Salah.mask(ctx, today) }
    val (nextIdx, nextAt, tomorrow) = Salah.next(ctx, now)
    val count = Integer.bitCount(mask)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RedDot(7.dp)
                Spacer(Modifier.width(8.dp))
                NLabel(if (tomorrow) "Next prayer · tomorrow" else "Next prayer")
            }
            Spacer(Modifier.height(16.dp))
            BigDot(PRAYERS[nextIdx], NWhite, 8.dp)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                NText(Fmt.time(ctx, nextAt), 20.sp)
                Spacer(Modifier.weight(1f))
                NText("IN " + Fmt.countdown(nextAt - now), 13.sp, NRed)
            }
        }

        NCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NLabel("Today", Modifier.weight(1f))
                NText("$count/5  ·  ${count * 20}%", 12.sp, NWhite)
            }
            Spacer(Modifier.height(12.dp))
            SegmentProgress(count)
            Spacer(Modifier.height(10.dp))
            for (i in 0..4) {
                val done = mask and (1 shl i) != 0
                val started = now >= t.prayers[i]
                val isNext = !tomorrow && i == nextIdx
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (!done && !started) {
                            Toast.makeText(ctx, "${Fmt.title(PRAYERS[i])} starts at ${Fmt.time(ctx, t.prayers[i])}", Toast.LENGTH_SHORT).show()
                        } else {
                            Salah.toggle(ctx, today, i)
                            local++
                            changed()
                        }
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        NText(PRAYERS[i], 15.sp, if (isNext) NRed else if (started) NWhite else NGrey, spacing = 1.sp)
                        if (i == 0) NText("SUNRISE " + Fmt.time(ctx, t.sunrise), 10.sp, NGrey)
                    }
                    NText(Fmt.time(ctx, t.prayers[i]), 15.sp, if (isNext) NRed else if (started) NWhite else NGrey)
                    Spacer(Modifier.width(14.dp))
                    Tick(done, started)
                }
            }
            Spacer(Modifier.height(6.dp))
            NText("Tap a prayer after you pray it.", 11.sp, NGrey)
        }

        val (full, days) = remember(key, local) { Salah.monthFull(ctx) }
        NCardBox {
            Row {
                Column(Modifier.weight(1f)) {
                    NLabel("This month")
                    Spacer(Modifier.height(4.dp))
                    NText("$full/$days full days", 15.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    NLabel("Streak")
                    Spacer(Modifier.height(4.dp))
                    NText("${remember(key, local) { Salah.streak(ctx) }} days", 15.sp, NRed)
                }
            }
            Spacer(Modifier.height(12.dp))
            DotProgress(if (days > 0) full / days.toFloat() else 0f)
        }
    }
}

// ---------------------------------------------------------------- MONTH
@Composable
fun MonthTab(key: Int, changed: () -> Unit) {
    val ctx = LocalContext.current
    val today = Salah.today()
    var month by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var local by remember { mutableIntStateOf(0) }
    var editDay by remember { mutableStateOf<LocalDate?>(null) }
    val v = key + local

    val first = month
    val len = first.lengthOfMonth()
    val counts = remember(v, month) { IntArray(len) { Salah.count(ctx, first.plusDays(it.toLong())) } }
    val (full, days) = remember(v, month) { Salah.monthFull(ctx, month) }
    val prayed = (0 until len).filter { !first.plusDays(it.toLong()).isAfter(today) }.sumOf { counts[it] }
    val consistency = if (days > 0) prayed * 100 / (days * 5) else 0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NButton("<") { month = month.minusMonths(1) }
            NText(Fmt.monthTitle(month), 14.sp, modifier = Modifier.weight(1f), align = TextAlign.Center, spacing = 2.sp)
            NButton(">") { if (month.plusMonths(1) <= today.withDayOfMonth(1)) month = month.plusMonths(1) }
        }

        NCardBox {
            NLabel("Days with all 5 prayers")
            Spacer(Modifier.height(14.dp))
            BigDot("$full/$days", NWhite, 8.dp)
            Spacer(Modifier.height(14.dp))
            Row {
                Column(Modifier.weight(1f)) { NLabel("Consistency"); NText("$consistency%", 15.sp) }
                Column(Modifier.weight(1f)) { NLabel("Prayers"); NText("$prayed/${days * 5}", 15.sp) }
                Column(Modifier.weight(1f)) { NLabel("Streak"); NText("${Salah.streak(ctx)}D", 15.sp, NRed) }
            }
        }

        // Calendar of dots
        NCardBox {
            NLabel("Calendar")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    NText(it, 10.sp, NGrey, Modifier.weight(1f), align = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(6.dp))
            val offset = first.dayOfWeek.value - 1 // Monday = 0
            val cells = offset + len
            val rows = (cells + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (cIdx in 0 until 7) {
                        val dayNum = r * 7 + cIdx - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(0.85f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..len) {
                                val date = first.plusDays((dayNum - 1).toLong())
                                DayCell(dayNum, counts[dayNum - 1], date == today, date.isAfter(today)) {
                                    if (!date.isAfter(today)) editDay = date
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(NWhite))
                NText("  ALL 5    ", 10.sp, NGrey)
                RedDot(6.dp)
                NText("  PRAYED    ", 10.sp, NGrey)
                NText("TAP A DAY TO EDIT", 10.sp, NGrey)
            }
        }

        // Daily consistency chart
        NCardBox {
            NLabel("Daily chart")
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                val cell = size.width / len
                val rowH = size.height / 5
                val r = minOf(cell, rowH) * 0.36f
                for (d in 0 until len) {
                    val future = first.plusDays(d.toLong()).isAfter(today)
                    for (k in 0 until 5) {
                        val lit = !future && k < counts[d]
                        val col = when {
                            lit && counts[d] == 5 -> NWhite
                            lit -> NRed
                            else -> NGhost
                        }
                        drawCircle(col, r, Offset(cell * d + cell / 2f, size.height - rowH * k - rowH / 2f))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                NText("1", 10.sp, NGrey)
                Spacer(Modifier.weight(1f))
                NText("$len", 10.sp, NGrey)
            }
        }

        // Per prayer
        NCardBox {
            NLabel("By prayer")
            Spacer(Modifier.height(6.dp))
            for (i in 0..4) {
                val n = remember(v, month, i) {
                    (0 until len).count { d ->
                        val date = first.plusDays(d.toLong())
                        !date.isAfter(today) && Salah.isDone(ctx, date, i)
                    }
                }
                val frac = if (days > 0) n / days.toFloat() else 0f
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NText(PRAYERS[i], 13.sp, modifier = Modifier.weight(1f))
                        NText("$n/$days  ", 11.sp, NGrey)
                        NText("${(frac * 100).roundToInt()}%", 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    DotProgress(frac, count = 30, lit = if (frac >= 0.8f) NWhite else NRed)
                }
            }
        }
    }

    editDay?.let { d ->
        var m by remember(d) { mutableIntStateOf(Salah.mask(ctx, d)) }
        AlertDialog(
            onDismissRequest = { editDay = null },
            containerColor = NCard,
            title = { NText(Fmt.date(d), 15.sp, spacing = 1.sp) },
            text = {
                Column {
                    for (i in 0..4) {
                        val done = m and (1 shl i) != 0
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                Salah.set(ctx, d, i, !done)
                                m = Salah.mask(ctx, d)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NText(PRAYERS[i], 14.sp, modifier = Modifier.weight(1f))
                            Tick(done, true, 26.dp)
                        }
                    }
                }
            },
            confirmButton = { NButton("Done", filled = true) { editDay = null; local++; changed() } }
        )
    }
}

@Composable
fun DayCell(day: Int, count: Int, isToday: Boolean, future: Boolean, onClick: () -> Unit) {
    val full = count == 5
    Column(
        Modifier.fillMaxSize()
            .clip(CircleShape)
            .then(if (isToday) Modifier.border(1.dp, NRed, CircleShape) else Modifier)
            .background(if (full) NWhite else Color.Transparent)
            .clickable(enabled = !future, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NText("$day", 11.sp, if (full) NBlack else if (future) NLine else NWhite)
        if (!full) {
            Spacer(Modifier.height(3.dp))
            Canvas(Modifier.width(22.dp).height(4.dp)) {
                val cell = size.width / 5
                for (k in 0 until 5) {
                    drawCircle(if (k < count && !future) NRed else NGhost, cell * 0.32f, Offset(cell * k + cell / 2f, size.height / 2f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- SETUP
@Composable
fun SetupTab(key: Int, changed: () -> Unit) {
    val ctx = LocalContext.current
    var local by remember { mutableIntStateOf(0) }
    val v = key + local
    val update: () -> Unit = { local++; changed() }
    val t = remember(v) { Salah.times(ctx, Salah.today()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NCardBox {
            NLabel("Time format")
            Spacer(Modifier.height(12.dp))
            val is24 = Settings.use24(ctx)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NButton("12 hour", filled = !is24, modifier = Modifier.weight(1f)) { Settings.setUse24(ctx, false); update() }
                NButton("24 hour", filled = is24, modifier = Modifier.weight(1f)) { Settings.setUse24(ctx, true); update() }
            }
        }

        NCardBox {
            NLabel("Location")
            Spacer(Modifier.height(12.dp))
            val sel = Settings.cityIndex(ctx)
            CITIES.indices.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    pair.forEach { i ->
                        NButton(CITIES[i].name, filled = i == sel, modifier = Modifier.weight(1f)) { Settings.setCity(ctx, i); update() }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        NCardBox {
            NLabel("Asr method")
            Spacer(Modifier.height(12.dp))
            val h = Settings.hanafi(ctx)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NButton("Hanafi", filled = h, modifier = Modifier.weight(1f)) { Settings.setHanafi(ctx, true); update() }
                NButton("Standard", filled = !h, modifier = Modifier.weight(1f)) { Settings.setHanafi(ctx, false); update() }
            }
            Spacer(Modifier.height(8.dp))
            NText("Hanafi is used in Bangladesh.", 11.sp, NGrey)
        }

        NCardBox {
            NLabel("Adjust times (minutes)")
            Spacer(Modifier.height(6.dp))
            NText("Defaults match the Islamic Foundation Bangladesh timetable. Change to match your local mosque.", 11.sp, NGrey)
            Spacer(Modifier.height(8.dp))
            for (i in 0..4) {
                val a = Settings.adj(ctx, i)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        NText(PRAYERS[i], 13.sp)
                        NText(Fmt.time(ctx, t.prayers[i]), 11.sp, NGrey)
                    }
                    NButton("-") { Settings.setAdj(ctx, i, a - 1); update() }
                    NText(if (a > 0) "+$a" else "$a", 14.sp, modifier = Modifier.width(48.dp), align = TextAlign.Center)
                    NButton("+") { Settings.setAdj(ctx, i, a + 1); update() }
                }
            }
            Spacer(Modifier.height(8.dp))
            NButton("Reset to default", modifier = Modifier.fillMaxWidth()) { Settings.resetAdj(ctx); update() }
        }

        NCardBox {
            NLabel("Reminders")
            Spacer(Modifier.height(12.dp))
            val n = Settings.notify(ctx)
            val vib = Settings.vibrate(ctx)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NButton(if (n) "Notify: on" else "Notify: off", filled = n, modifier = Modifier.weight(1f)) { Settings.setNotify(ctx, !n); update() }
                NButton(if (vib) "Vibrate: on" else "Vibrate: off", filled = vib, modifier = Modifier.weight(1f)) { Settings.setVibrate(ctx, !vib); update() }
            }
            Spacer(Modifier.height(10.dp))
            NButton("Send test notification", modifier = Modifier.fillMaxWidth()) {
                if (Notifier.canPost(ctx)) Notifier.test(ctx)
                else Toast.makeText(ctx, "Allow notifications for Dot Salah in phone settings", Toast.LENGTH_LONG).show()
            }
            Spacer(Modifier.height(10.dp))
            NButton("Allow in background (battery)", modifier = Modifier.fillMaxWidth()) {
                runCatching {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            Spacer(Modifier.height(8.dp))
            NText("For on-time reminders, set Dot Salah battery use to \"Unrestricted\".", 11.sp, NGrey)
        }

        NCardBox {
            NLabel("Home screen widget")
            Spacer(Modifier.height(8.dp))
            NText(
                "Long-press your home screen > Widgets > Dot Salah. Drag it on, then long-press and drag the edges to resize. Tap a prayer row on the widget to tick it.",
                12.sp, NGrey
            )
        }

        NText("Times: University of Islamic Sciences, Karachi method (18°/18°).", 10.sp, NGrey)
    }
}
