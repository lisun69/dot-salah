package com.abidur.dotsalah

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

val NRed = Color(0xFFD71921)
val NBlack = Color(0xFF000000)
val NCard = Color(0xFF0D0D0D)
val NLine = Color(0xFF262626)
val NGrey = Color(0xFF8A8A8A)
val NWhite = Color(0xFFFFFFFF)
val NGhost = Color(0x22FFFFFF)
val Mono = FontFamily.Monospace

@Composable
fun NothingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NRed, onPrimary = NWhite,
            background = NBlack, onBackground = NWhite,
            surface = NCard, onSurface = NWhite,
            surfaceVariant = NCard, onSurfaceVariant = NGrey,
            outline = NLine
        ),
        content = content
    )
}

@Composable
fun NText(
    text: String,
    size: TextUnit = 13.sp,
    color: Color = NWhite,
    modifier: Modifier = Modifier,
    spacing: TextUnit = 0.sp,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Start
) {
    Text(
        text = text, modifier = modifier, color = color, fontSize = size, fontFamily = Mono,
        letterSpacing = spacing, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align
    )
}

@Composable
fun NLabel(text: String, modifier: Modifier = Modifier, color: Color = NGrey) {
    NText(text.uppercase(), 11.sp, color, modifier, spacing = 1.5.sp, maxLines = 1)
}

@Composable
fun RedDot(size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(NRed))
}

@Composable
fun NCardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(NCard).border(1.dp, NLine, shape).padding(18.dp),
        content = content
    )
}

@Composable
fun NButton(text: String, filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape)
            .background(if (filled) NRed else Color.Transparent)
            .border(1.dp, if (filled) NRed else NLine, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        NText(text.uppercase(), 12.sp, NWhite, spacing = 1.sp, maxLines = 1)
    }
}

@Composable
fun DotText(text: String, dot: Dp, color: Color, modifier: Modifier = Modifier, ghost: Color = Color.Transparent) {
    val cols = DotFont.columns(text).coerceAtLeast(1)
    Canvas(modifier.size(dot * cols, dot * 7)) {
        val cell = size.height / 7f
        val r = cell * 0.38f
        DotFont.forEachDot(text) { x, y, on ->
            val col = if (on) color else ghost
            if (col.alpha > 0f) drawCircle(col, r, Offset(x * cell + cell / 2f, y * cell + cell / 2f))
        }
    }
}

@Composable
fun BigDot(text: String, color: Color, maxDot: Dp = 9.dp, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = DotFont.columns(text).coerceAtLeast(1)
        val fit = maxWidth / cols
        val dot = if (fit < maxDot) fit else maxDot
        DotText(text, dot, color, ghost = NGhost)
    }
}

@Composable
fun DotProgress(fraction: Float, modifier: Modifier = Modifier, count: Int = 24, lit: Color = NWhite) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val cell = size.width / count
        val r = minOf(cell, size.height) * 0.38f
        val on = (fraction.coerceIn(0f, 1f) * count).roundToInt()
        for (i in 0 until count) {
            drawCircle(if (i < on) lit else NGhost, r, Offset(cell * i + cell / 2f, size.height / 2f))
        }
    }
}

/** 5 segments x 4 dots: each prayer = 20%. */
@Composable
fun SegmentProgress(done: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val perSeg = 4
        val total = 5 * perSeg + 4
        val cell = size.width / total
        val r = minOf(cell, size.height) * 0.38f
        var x = 0
        for (s in 0 until 5) {
            for (d in 0 until perSeg) {
                drawCircle(if (s < done) NRed else NGhost, r, Offset(cell * x + cell / 2f, size.height / 2f))
                x++
            }
            x++
        }
    }
}

/** Round tick: filled red with white check when done, white ring when due, faint ring when not yet. */
@Composable
fun Tick(done: Boolean, due: Boolean, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        if (done) {
            drawCircle(NRed, r, c)
            val w = this.size.width
            val h = this.size.height
            val p = Path()
            p.moveTo(w * 0.29f, h * 0.52f)
            p.lineTo(w * 0.44f, h * 0.67f)
            p.lineTo(w * 0.71f, h * 0.36f)
            drawPath(p, NWhite, style = Stroke(width = r * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawCircle(if (due) NWhite else NGhost, r - r * 0.08f, c, style = Stroke(width = r * 0.14f))
        }
    }
}
