package app.grip_gains_companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.service.offline.OfflineCurve
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OfflineCurvePanel(curve: OfflineCurve?, savedAt: Long?, lbs: Boolean, weight: Double?, select: (OfflineCurve.Match) -> Unit) {
    Text("Force curve",style=MaterialTheme.typography.titleMedium)
    if(curve==null) {
        Text("No saved curve for this gripper and hand. Connect and sign in to cache your personal curve. At least five sessions and supporting zone data are required.",style=MaterialTheme.typography.bodySmall)
        return
    }
    val unit=if(lbs) "lb" else "kg"
    val factor=if(lbs) 1.0 else 0.45359237
    val maxWeight=(curve.pounds(30.0)*factor).coerceAtLeast(0.001)
    val colors=listOf(Color(0xFFD43B3D),Color(0xFFC44786),Color(0xFF5599FF),Color(0xFF65C936),Color(0xFFD4B344))
    Text("Weight ($unit) vs hold time (s)",style=MaterialTheme.typography.labelMedium)
    Row {
        Column(Modifier.height(140.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Text(String.format(Locale.US,"%.1f",maxWeight),style=MaterialTheme.typography.labelSmall)
            Text("0 $unit",style=MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.weight(1f).height(140.dp).padding(start=8.dp)) {
            val boundaries=listOf(30.0,48.0,82.0,129.0,180.0,300.0)
            for(i in 0..4) {
                val left=((boundaries[i]-30)/270*size.width).toFloat()
                val right=((boundaries[i+1]-30)/270*size.width).toFloat()
                drawRect(colors[i].copy(alpha=0.12f),Offset(left,0f),androidx.compose.ui.geometry.Size(right-left,size.height))
                val path=Path()
                for(n in 0..40) {
                    val t=boundaries[i]+(boundaries[i+1]-boundaries[i])*n/40
                    val x=((t-30)/270*size.width).toFloat()
                    val y=(size.height*(1-curve.pounds(t)*factor/maxWeight)).toFloat().coerceIn(0f,size.height)
                    if(n==0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                drawPath(path,colors[i],style=Stroke(2.dp.toPx()))
            }
            weight?.takeIf {it.isFinite() && it>0}?.let { curve.estimate(it,lbs) }?.let { match ->
                drawCircle(Color.White,4.dp.toPx(),Offset((match.seconds-30)/270f*size.width,(size.height*(1-match.weight/maxWeight)).toFloat().coerceIn(0f,size.height)))
            }
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("30 s");Text("165 s");Text("300 s")}
    val estimate=weight?.takeIf { it.isFinite() && it>0 }?.let {curve.estimate(it,lbs)}
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        OfflineCurve.zones.forEachIndexed { index, zone ->
            val match=curve.match(index,lbs)
            FilterChip(selected=estimate?.zone==zone,enabled=match!=null,onClick={match?.let(select)},label={Text(zone.label)})
        }
    }
    Text(estimate?.let { "${it.zone.label} · ${it.seconds} s estimated hold · ${it.zone.reps} recommended reps" }
        ?: "Enter a weight or choose a supported zone. Unavailable zones need more training data.",style=MaterialTheme.typography.bodySmall)
    var time by rememberSaveable {mutableStateOf("")}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(time,{time=it},label={Text("Match hold time (30–300 s)")},singleLine=true,modifier=Modifier.weight(1f))
        val seconds=time.toDoubleOrNull()
        val match=seconds?.takeIf {it.isFinite() && it in 30.0..300.0}?.let {curve.estimate(curve.pounds(it)*factor,lbs)}
        TextButton(onClick={match?.let(select)},enabled=match!=null) {Text("Match")}
    }
    if(savedAt!=null) Text("Saved curve · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(savedAt))}. Pending sets are included after sync and curve refresh.",style=MaterialTheme.typography.bodySmall)
}
