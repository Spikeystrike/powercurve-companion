package app.grip_gains_companion.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.model.ForceHistoryEntry
import kotlinx.coroutines.delay
import app.grip_gains_companion.service.graphTimeMillis
import app.grip_gains_companion.service.visibleForceHistory

/** Draw real sensor samples. Gaps stay gaps; no fabricated 60 Hz force trail. */
@Composable
fun ForceGraph(forceHistory: List<ForceHistoryEntry>, useLbs: Boolean, windowSeconds: Int = 5,
               targetWeight: Double? = null, tolerance: Double? = null, isReconnecting: Boolean = false) {
    val lineColor = MaterialTheme.colorScheme.primary
    var now by remember { mutableLongStateOf(graphTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = graphTimeMillis(); delay(250) } }
    val points = remember(forceHistory, windowSeconds) { visibleForceHistory(forceHistory, windowSeconds) }
    val factor = if (useLbs) 2.20462 else 1.0
    val unit = if (useLbs) "lb" else "kg"
    Canvas(Modifier.fillMaxWidth().fillMaxHeight().padding(8.dp)) {
        val left = 42.dp.toPx(); val bottom = size.height - 22.dp.toPx()
        val width = size.width - left; val height = bottom - 12.dp.toPx()
        if (height <= 0 || width <= 0) return@Canvas
        val duration = windowSeconds.coerceIn(1,60) * 1000L
        val cutoff = (forceHistory.lastOrNull()?.timestamp?.time ?: now) - duration
        val yMax = maxOf(5.0, points.maxOfOrNull { it.force * factor } ?: 0.0,
            ((targetWeight ?: 0.0) + (tolerance ?: 0.0)) * factor) * 1.15
        fun y(value: Double) = bottom - (value.coerceIn(0.0, yMax) / yMax * height).toFloat()
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.LTGRAY; textSize = 10.dp.toPx() }
        for (i in 0..4) {
            val value = yMax * i / 4
            drawLine(Color.White.copy(alpha=.12f), androidx.compose.ui.geometry.Offset(left,y(value)), androidx.compose.ui.geometry.Offset(size.width,y(value)))
            drawContext.canvas.nativeCanvas.drawText("%.0f".format(value), 0f, y(value), labelPaint)
        }
        drawContext.canvas.nativeCanvas.drawText(unit, 0f, 10.dp.toPx(), labelPaint)
        drawContext.canvas.nativeCanvas.drawText("−${windowSeconds}s", left, size.height, labelPaint)
        drawContext.canvas.nativeCanvas.drawText("0s", size.width - 18.dp.toPx(), size.height, labelPaint)
        targetWeight?.takeIf { it > 0 }?.let { target ->
            val pos = y(target * factor)
            drawLine(Color(0xFFFFC86B), androidx.compose.ui.geometry.Offset(left,pos), androidx.compose.ui.geometry.Offset(size.width,pos), strokeWidth=1.dp.toPx())
        }
        val path = Path()
        var previous: Long? = null
        points.forEach { p ->
            val time = p.timestamp.time
            val x = left + ((time - cutoff).toDouble() / duration * width).toFloat()
            val py = y(p.force * factor)
            if (previous == null || time - previous!! > 1500) path.moveTo(x, py) else path.lineTo(x, py)
            previous = time
        }
        drawPath(path, if (isReconnecting) Color.Gray else lineColor, style=Stroke(width=2.dp.toPx()))
        if (isReconnecting || (forceHistory.lastOrNull()?.let { now - it.timestamp.time > 1500 } == true)) {
            drawContext.canvas.nativeCanvas.drawText("Waiting for measurements…", left + 8.dp.toPx(), 20.dp.toPx(), labelPaint)
        }
    }
}
