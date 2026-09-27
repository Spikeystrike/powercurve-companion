package app.grip_gains_companion.ui.components

import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.service.offline.OfflineCurve
import app.grip_gains_companion.service.offline.OfflineHistorySet
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OfflineCurvePanel(curve: OfflineCurve?, savedAt: Long?, lbs: Boolean, weight: Double?, history: List<OfflineHistorySet> = emptyList(), targetSeconds: Int? = null, select: (OfflineCurve.Match) -> Unit) {
    Text("Force curve",style=MaterialTheme.typography.titleMedium)
    val points=remember(history) {history.take(60)}
    if(curve==null) Text("No saved fitted curve for this gripper and hand. Connect and sign in to refresh it. Saved sets are shown when available.",style=MaterialTheme.typography.bodySmall)
    if(curve==null && points.isEmpty()) return
    val unit=if(lbs) "lb" else "kg"
    val factor=if(lbs) 1.0 else 0.45359237
    val curveRange=remember(curve) {curve?.plotRange()}
    val minPounds=remember(curveRange,points,weight) { minOf(weight?.takeIf {it.isFinite() && it>0}?.let {it/factor*0.95} ?: Double.MAX_VALUE,curveRange?.minimum ?: Double.MAX_VALUE,points.minOfOrNull {it.pounds*0.95} ?: Double.MAX_VALUE) }
    val maxPounds=remember(curveRange,points,weight) { maxOf(weight?.takeIf {it.isFinite() && it>0}?.let {it/factor*1.02} ?: 0.0,curveRange?.maximum ?: 0.0,points.maxOfOrNull {it.pounds*1.02} ?: 0.0,minPounds+0.001) }
    val minTime=0.0
    val maxTime=300.0
    val timeSpan=maxTime-minTime
    val minWeight=minPounds*factor;val maxWeight=maxPounds*factor;val weightSpan=maxWeight-minWeight
    val currentSelect by rememberUpdatedState(select)
    var tapMessage by remember(curve) {mutableStateOf("")}
    val inspected=remember(curve,lbs,weight) {
        weight?.takeIf {it.isFinite() && it>0}?.let { value ->
            val pounds=value/factor
            curve?.hold(pounds)?.takeIf {it.isFinite() && it>0}?.let {OfflineCurve.PlotPoint(pounds,it)}
        }
    }
    val segments=remember(curve,minPounds,maxPounds,minTime,maxTime) {curve?.plotSegments()?.map {segment->segment.map {point->
        Offset(((point.weightPounds-minPounds)/(maxPounds-minPounds)).toFloat(),((maxTime-point.seconds)/timeSpan).toFloat())
    }} ?: emptyList()}
    val colors=remember {listOf(Color(0xFFD43B3D),Color(0xFFC44786),Color(0xFF5599FF),Color(0xFF65C936),Color(0xFFD4B344))}
    val estimate=remember(curve,lbs,weight) {weight?.takeIf {it.isFinite() && it>0}?.let {curve?.estimate(it,lbs)}}
    val matches=remember(curve,lbs) {OfflineCurve.zones.indices.map {curve?.match(it,lbs)}}
    var zoneTouched by remember {mutableStateOf(false)}
    var selectedZone by remember {mutableStateOf<Int?>(OfflineHistorySet.oldestZone(history))}
    LaunchedEffect(targetSeconds,estimate?.zone) {selectedZone=targetSeconds?.let {OfflineCurve.zone(it.toDouble())} ?: estimate?.zone?.let {OfflineCurve.zones.indexOf(it)} ?: selectedZone}
    LaunchedEffect(history) {if(!zoneTouched && estimate==null) selectedZone=OfflineHistorySet.oldestZone(history)}
    val latestByZone=remember(history) {history.groupBy {it.zone}.mapValues {it.value.first()}}
    Text("Hold time (s) vs weight ($unit)",style=MaterialTheme.typography.labelMedium)
    Row {
        Box(Modifier.width(64.dp).height(170.dp)) {
            Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.SpaceBetween) {
                for(tick in listOf(maxTime,(minTime+maxTime)/2,minTime)) {
                    val near=inspected?.let {it.seconds in minTime..maxTime && kotlin.math.abs(it.seconds-tick)<timeSpan*0.08} == true
                    Text(if(near) "" else tick.roundToInt().toString()+" s",style=MaterialTheme.typography.labelSmall)
                }
            }
            inspected?.takeIf {it.seconds in minTime..maxTime}?.let {point->
                Text(String.format(Locale.US,"%.1f s",point.seconds),color=Color(0xFFFFC86B),style=MaterialTheme.typography.labelSmall,
                    modifier=Modifier.testTag("selected-time-axis").layout {measurable,constraints->
                        val label=measurable.measure(constraints.copy(minWidth=0,minHeight=0))
                        layout(constraints.maxWidth,constraints.maxHeight) {
                            val y=((maxTime-point.seconds)/timeSpan*constraints.maxHeight-label.height/2).toInt().coerceIn(0,(constraints.maxHeight-label.height).coerceAtLeast(0))
                            label.placeRelative(0,y)
                        }
                    })
            }
        }
        Column(Modifier.weight(1f).padding(start=8.dp)) {
            Box(Modifier.fillMaxWidth().height(170.dp).clipToBounds().testTag("offline-curve-plot")
                .pointerInput(curve,lbs,minPounds,maxPounds) {
                    detectTapGestures {position->
                        if(size.width>0 && curve!=null && curveRange!=null) {
                            val pounds=minPounds+position.x.toDouble()/size.width*(maxPounds-minPounds)
                            val point=curve.inspectPlot((pounds-curveRange.minimum)/curveRange.span,curveRange)
                            val match=curve.estimate(point.weightPounds*factor,lbs)
                            if(match!=null) {tapMessage="";currentSelect(match)}
                            else tapMessage="Not enough training data for a recommendation in this zone."
                        }
                    }
                }.drawWithCache {
                    val paths=segments.map {segment->Path().apply {segment.forEachIndexed {index,point->
                        val x=point.x*size.width;val y=point.y*size.height
                        if(index==0) moveTo(x,y) else lineTo(x,y)
                    }}}
                    val bounds=listOf(minTime,48.0,82.0,129.0,180.0,maxTime)
                    fun position(pounds: Double,seconds: Double)=Offset(((pounds-minPounds)/(maxPounds-minPounds)*size.width).toFloat(),((maxTime-seconds)/timeSpan*size.height).toFloat())
                    onDrawBehind {
                        for(i in 0..4) {
                            val top=((maxTime-bounds[i+1])/timeSpan*size.height).toFloat()
                            val bottom=((maxTime-bounds[i])/timeSpan*size.height).toFloat()
                            drawRect(colors[i].copy(alpha=0.12f),Offset(0f,top),androidx.compose.ui.geometry.Size(size.width,bottom-top))
                            paths.getOrNull(i)?.let {drawPath(it,colors[i],style=Stroke(2.dp.toPx()))}
                        }
                        // Oldest first, so newer points remain visible where sets overlap.
                        points.indices.reversed().forEach {rank->
                            val set=points[rank]
                            drawCircle(Color(0xFF5599FF).copy(alpha=OfflineHistorySet.opacity(rank,points.size)),3.5.dp.toPx(),position(set.pounds,set.hold))
                        }
                        latestByZone.values.filter {it in points}.forEach {set->
                            val center=position(set.pounds,set.hold)
                            drawCircle(colors[set.zone],5.dp.toPx(),center)
                            drawCircle(Color.White.copy(alpha=0.8f),5.dp.toPx(),center,style=Stroke(1.dp.toPx()))
                        }
                        inspected?.let {point->
                            val pos=position(point.weightPounds,point.seconds)
                            if(point.seconds in minTime..maxTime) {
                                val guideColor=Color(0xFFFFC86B).copy(alpha=0.7f)
                                drawLine(guideColor,Offset(pos.x,size.height),pos,1.dp.toPx())
                                drawLine(guideColor,Offset(0f,pos.y),pos,1.dp.toPx())
                                drawCircle(Color(0xFFFFC86B),5.dp.toPx(),pos)
                            }
                        }

                    }
                })
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                listOf(minWeight,(minWeight+maxWeight)/2,maxWeight).forEach {Text(String.format(Locale.US,"%.1f %s",it,unit),style=MaterialTheme.typography.labelSmall)}
            }
            inspected?.let {point->
                Text(String.format(Locale.US,"%.2f %s",point.weightPounds*factor,unit),color=Color(0xFFFFC86B),style=MaterialTheme.typography.labelSmall,
                    modifier=Modifier.fillMaxWidth().testTag("selected-weight-axis").layout {measurable,constraints->
                        val label=measurable.measure(constraints.copy(minWidth=0))
                        layout(constraints.maxWidth,label.height) {
                            val x=((point.weightPounds-minPounds)/(maxPounds-minPounds)*constraints.maxWidth-label.width/2).toInt().coerceIn(0,(constraints.maxWidth-label.width).coerceAtLeast(0))
                            label.placeRelative(x,0)
                        }
                    })
            }
        }
    }
    if(tapMessage.isNotEmpty()) Text(tapMessage,color=MaterialTheme.colorScheme.error)
    Text("${points.size} recent sets · older sets fade by order · outlined points: latest per time zone",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("offline-history-count"))
    Text(inspected?.let {String.format(Locale.US,"Curve point: %.2f %s · %.1f s",it.weightPounds*factor,unit,it.seconds)}
        ?: if(curve!=null) "Tap the curve to set target weight and hold time." else "Saved sets are available without a fitted curve.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("offline-curve-readout"))
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        OfflineCurve.zones.indices.reversed().forEach {index->
            val zone=OfflineCurve.zones[index];val match=matches[index]
            FilterChip(selected=selectedZone==index,onClick={zoneTouched=true;selectedZone=index;match?.let(select)},label={Text(zone.label)})
        }
    }
    selectedZone?.let {index->
        val last=latestByZone[index]
        if(last==null) Text("No saved set in ${OfflineCurve.zones[index].label}.",style=MaterialTheme.typography.bodySmall)
        else Text("Last ${OfflineCurve.zones[index].label} set · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(last.timestamp))}\n"+
            String.format(Locale.US,"%.2f %s · %.1f s · %d days ago%s",last.pounds*factor,unit,last.hold,last.daysAgo(),if(last.pending) " · waiting to sync" else ""),style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("offline-last-zone-set"))
    }
    Text(estimate?.let {"${it.zone.label} · ${it.seconds} s estimated hold · ${it.zone.reps} recommended reps"}
        ?: "Choose a zone to view its last set. Recommendations require supporting curve data.",style=MaterialTheme.typography.bodySmall)
    var time by rememberSaveable {mutableStateOf("")}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(time,{time=it},label={Text("Match hold time (1–400 s)")},singleLine=true,modifier=Modifier.weight(1f))
        val seconds=time.replace(',','.').toDoubleOrNull()
        val match=seconds?.let {curve?.matchTime(it,lbs)}
        TextButton(onClick={match?.let(select)},enabled=match!=null) {Text("Match")}
    }
    if(savedAt!=null) Text("Saved curve · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(savedAt))}. Local sets appear immediately; the fit updates after sync.",style=MaterialTheme.typography.bodySmall)
}
