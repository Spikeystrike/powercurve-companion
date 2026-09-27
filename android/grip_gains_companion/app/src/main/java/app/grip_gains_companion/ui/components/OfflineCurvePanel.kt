package app.grip_gains_companion.ui.components

import androidx.compose.ui.draw.drawWithCache
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
    val maxWeight=remember(curve,lbs) {(curve.pounds(30.0)*factor).coerceAtLeast(0.001)}
    // Sample once per curve/unit; no curve solving or JSON access during drawing/scrolling.
    val segments=remember(curve,lbs) { curve.plotSegments().map { segment -> segment.map { point ->
        Offset((point.weightPounds*factor/maxWeight).toFloat(),((300-point.seconds)/270).toFloat())
    } } }
    val colors=remember {listOf(Color(0xFFD43B3D),Color(0xFFC44786),Color(0xFF5599FF),Color(0xFF65C936),Color(0xFFD4B344))}
    val estimate=remember(curve,lbs,weight) { weight?.takeIf {it.isFinite() && it>0}?.let {curve.estimate(it,lbs)} }
    val matches=remember(curve,lbs) { OfflineCurve.zones.indices.map {curve.match(it,lbs)} }
    Text("Hold time (s) vs weight ($unit)",style=MaterialTheme.typography.labelMedium)
    Row {
        Column(Modifier.height(140.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Text("300 s",style=MaterialTheme.typography.labelSmall)
            Text("165 s",style=MaterialTheme.typography.labelSmall)
            Text("30 s",style=MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f).padding(start=8.dp)) {
            Box(Modifier.fillMaxWidth().height(140.dp).drawWithCache {
                val paths=segments.map { segment -> Path().apply {
                    segment.forEachIndexed { index, point ->
                        val x=point.x.coerceIn(0f,1f)*size.width
                        val y=point.y.coerceIn(0f,1f)*size.height
                        if(index==0) moveTo(x,y) else lineTo(x,y)
                    }
                } }
                val bounds=listOf(30f,48f,82f,129f,180f,300f)
                onDrawBehind {
                    for(i in 0..4) {
                        val top=(300-bounds[i+1])/270*size.height
                        val bottom=(300-bounds[i])/270*size.height
                        drawRect(colors[i].copy(alpha=0.12f),Offset(0f,top),androidx.compose.ui.geometry.Size(size.width,bottom-top))
                        drawPath(paths[i],colors[i],style=Stroke(2.dp.toPx()))
                    }
                    estimate?.let { match ->
                        drawCircle(Color.White,4.dp.toPx(),Offset((match.weight/maxWeight*size.width).toFloat().coerceIn(0f,size.width),(300-match.seconds)/270f*size.height))
                    }
                }
            })
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("0 $unit",style=MaterialTheme.typography.labelSmall)
                Text(String.format(Locale.US,"%.1f %s",maxWeight/2,unit),style=MaterialTheme.typography.labelSmall)
                Text(String.format(Locale.US,"%.1f %s",maxWeight,unit),style=MaterialTheme.typography.labelSmall)
            }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        OfflineCurve.zones.forEachIndexed { index, zone ->
            val match=matches[index]
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
