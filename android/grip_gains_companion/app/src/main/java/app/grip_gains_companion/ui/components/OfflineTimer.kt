package app.grip_gains_companion.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import app.grip_gains_companion.service.offline.OfflineCurve
import app.grip_gains_companion.service.offline.OfflineHistorySet
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.service.offline.OfflineTraining
import java.text.DateFormat
import java.util.Date

@Composable
fun OfflineStatus(training: OfflineTraining, useLbs: Boolean = false) {
    val revision by training.revision.collectAsState()
    @Suppress("UNUSED_VARIABLE") val refresh=revision
    var showPending by remember {mutableStateOf(false)}
    if(showPending) PendingSetsDialog(training,useLbs) {showPending=false;training.manageQueue(false)}
    if(training.pending==0 && training.synced==0 && training.error.isEmpty()) return
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal=12.dp,vertical=6.dp)) {
            if(training.pending>0) Text("${training.pending} set(s) waiting to sync",style=MaterialTheme.typography.labelLarge)
            if(training.pending>0) TextButton(onClick={training.manageQueue(true);showPending=true}) {Text("Review pending sets")}
            if(training.pending>0 && training.syncMessage.isNotBlank()) Text(training.syncMessage,style=MaterialTheme.typography.bodySmall)
            if(training.error.isNotBlank()) { Text(training.error,color=MaterialTheme.colorScheme.error); TextButton(onClick=training::retryStorage){Text("Retry storage")} }
            if(training.needsAccount) TextButton(onClick=training::assignUnowned) {Text("Sync to ${training.accountName.ifBlank { "this account" }}")}
            if(training.synced>0) Row(horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()) {
                Text("${training.synced} set(s) synced · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(training.state.optLong("syncedAt")))}",modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=training::dismissSuccess){Text("Dismiss")}
            }
        }
    }
}

@Composable
fun OfflineTimer(training: OfflineTraining, useLbs: Boolean, modifier: Modifier=Modifier) {
    val revision by training.revision.collectAsState()
    @Suppress("UNUSED_VARIABLE") val refresh=revision
    val defaults=remember {training.state.optJSONObject("defaults")?.takeIf {it.optString("gripper")=="micro" && it.optString("side")=="left"}}
    var gripper by rememberSaveable {mutableStateOf("micro")}
    var side by rememberSaveable {mutableStateOf("left")}
    var weight by rememberSaveable {mutableStateOf(defaults?.optDouble("weightLbs")?.let {displayNumber(if(useLbs) it else it*0.45359237)} ?: "")}
    var reps by rememberSaveable {mutableStateOf((defaults?.optInt("plannedReps") ?: 6).toString())}
    var rest by rememberSaveable {mutableStateOf((defaults?.optInt("rest") ?: 10).toString())}
    var countdown by rememberSaveable {mutableStateOf((defaults?.optInt("countdown") ?: 20).toString())}
    var targetTime by rememberSaveable {mutableStateOf(defaults?.optInt("targetDuration")?.takeIf {it>0}?.toString() ?: "")}
    var previousLbs by rememberSaveable {mutableStateOf(useLbs)}
    LaunchedEffect(useLbs) {
        if(previousLbs!=useLbs) {
            weight.replace(',','.').toDoubleOrNull()?.let { weight=displayNumber(if(useLbs) it/0.45359237 else it*0.45359237) }
            previousLbs=useLbs
        }
    }
    val lbs=weight.replace(',','.').toDoubleOrNull()?.let {if(useLbs)it else it/0.45359237}
    LaunchedEffect(weight,useLbs,gripper,side,targetTime,training.active) {
        training.previewTarget(lbs,gripper,side,targetTime.toIntOrNull())
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Column(modifier.background(Color(0xFF1A2231)).verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(training.online) "Local set timer" else "Offline set timer",style=MaterialTheme.typography.titleLarge)
        Text(if(training.historyCheckedAt>0) "History checked: "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(training.historyCheckedAt)) else "History has not been refreshed yet.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick=training::refreshHistory,enabled=training.online && !training.refreshing){Text(if(training.refreshing) "Updating…" else "Update now")}
        if(training.refreshMessage.isNotEmpty()) Text(training.refreshMessage,style=MaterialTheme.typography.bodySmall)
        val active=training.active
        if(active==null) {
            Text("Sets are saved on this phone and imported automatically when connected.",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("micro","crusher","prime").forEach {item->FilterChip(selected=gripper==item,onClick={if(gripper!=item){gripper=item;weight="";reps="";targetTime=""}},label={Text(item.replaceFirstChar(Char::uppercase))})} }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("left","right").forEach {item->FilterChip(selected=side==item,onClick={if(side!=item){side=item;weight="";reps="";targetTime=""}},label={Text(item.replaceFirstChar(Char::uppercase))})}}
            var edited by rememberSaveable(gripper,side) {mutableStateOf(false)}
            val curve=training.curve(gripper,side)
            val history=remember(revision,gripper,side) {training.history(gripper,side)}
            LaunchedEffect(curve,history,gripper,side) {
                if(!edited) curve?.match(OfflineHistorySet.oldestZone(history),useLbs)?.let {match->
                    weight=String.format(java.util.Locale.US,"%.2f",match.weight)
                    reps=match.zone.reps.toString();targetTime=match.seconds.toString()
                }
            }
            key(gripper,side) {
            OfflineCurvePanel(curve,training.curveSavedAt,useLbs,weight.replace(',','.').toDoubleOrNull(),history,targetTime.toIntOrNull()) { match ->
                edited=true
                weight=String.format(java.util.Locale.US,"%.2f",match.weight)
                reps=match.zone.reps.toString()
                targetTime=match.seconds.toString()
            }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(weight,{edited=true;weight=decimalInput(it);val match=curve?.estimate(weight.replace(',','.').toDoubleOrNull() ?: 0.0,useLbs);targetTime=match?.seconds?.toString() ?: "";if(match!=null) reps=match.zone.reps.toString()},label={Text("Weight (${if(useLbs) "lb" else "kg"})")},singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(reps,{edited=true;reps=it},label={Text("Reps (1–100)")},singleLine=true,modifier=Modifier.weight(1f))
            }
            OutlinedTextField(targetTime,{edited=true;targetTime=it},label={Text("Target hold (s, optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rest,{rest=it},label={Text("Rest (0–600 s)")},singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(countdown,{countdown=it},label={Text("Countdown (0–60 s)")},singleLine=true,modifier=Modifier.weight(1f))
            }
            Button(onClick={training.start(gripper,side,lbs!!,reps.toInt(),rest.toInt(),countdown.toInt(),targetTime.toIntOrNull())},enabled=(targetTime.isBlank() || targetTime.toIntOrNull() in 1..3600) && training.available && lbs!=null && lbs.isFinite() && lbs>0 && reps.toIntOrNull() in 1..100 && rest.toIntOrNull() in 0..600 && countdown.toIntOrNull() in 0..60,modifier=Modifier.fillMaxWidth()){Text("Start set")}
            if(training.realForceEnabled) {
                Button(onClick={training.startRealForce(gripper,side,lbs!!,reps.toInt(),rest.toInt(),targetTime.toIntOrNull())},
                    enabled=training.forceMeterConnected && training.available && lbs!=null && lbs.isFinite() && lbs>0 && reps.toIntOrNull() in 1..100 && rest.toIntOrNull() in 0..600 && (targetTime.isBlank() || targetTime.toIntOrNull() in 1..3600),modifier=Modifier.fillMaxWidth()) {Text("Start real force set")}
                Text(if(!training.forceMeterConnected) "Connect a force meter to use Real Force." else "No countdown: pull when ready. Starts at 90% of target and ends on force drop. Rep 1's "+training.realForceMethod+" becomes the saved weight and the target for all later reps.",style=MaterialTheme.typography.bodySmall)
            }
            if(training.online) TextButton(onClick=training::useWebsite){Text("Return to Powercurve")}
        } else {
            val done=active.getJSONArray("reps").length()
            Text("${active.getString("gripper")} · ${active.getString("side")} · " + String.format(java.util.Locale.US,"%.1f %s",active.getDouble("weightLbs")*if(useLbs) 1.0 else 0.45359237,if(useLbs) "lb" else "kg"))
            Text(if(training.phase=="ready") "Pull when ready" else "${training.phase.replaceFirstChar(Char::uppercase)} · ${training.seconds}s",style=MaterialTheme.typography.headlineLarge)
            if(training.isRealForce) {
                Text("Real Force · "+active.optString("realMethod").replaceFirstChar(Char::uppercase))
                if(training.phase=="ready") Text("Reach "+displayNumber(active.getDouble("weightLbs")*0.9*(if(useLbs) 1.0 else 0.45359237))+" "+(if(useLbs) "lb" else "kg")+" to start timing.")
                if(training.realMessage.isNotBlank()) Text(training.realMessage)
                if(training.phase=="save_failed") Button(onClick=training::retryMeasuredRep) {Text("Retry saving measured rep")}
            }
            if(active.optInt("targetDuration")>0) {
                Text("Target hold: ${active.getInt("targetDuration")} s")
                if(training.phase=="rep" && done==0) Text("Target countdown: ${training.targetRemaining} s",style=MaterialTheme.typography.headlineMedium)
            }
            Text("$done / ${active.getInt("plannedReps")} reps completed")
            if(training.phase=="paused") {
                Text("Interrupted set recovered. Completed reps are safe; the interrupted rep was not counted.")
                Button(onClick=training::resumeRecovered,enabled=!training.isRealForce || training.forceMeterConnected){Text("Continue set")}
            }
            val complete=done>=active.getInt("plannedReps")
            val actionKey=active.getString("id")+":"+training.phase+":"+done
            if(complete) Button(onClick=training::finish,modifier=Modifier.fillMaxWidth()){Text("Save set")}
            else if(training.phase=="rep" && training.isRealForce) Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()){Text("Measuring force")}
            else if(training.phase=="rep") Button(onClick=training::endRep,modifier=Modifier.fillMaxWidth()){Text("End rep")}
            else Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()){Text("Locked")}
            if(!complete) HoldToConfirm("Hold to save set now",enabled=done>0 && training.phase !in listOf("rep","save_failed"),resetKey=actionKey,action=training::finish)
            HoldToConfirm("Discard set without saving",resetKey=actionKey,action=training::discardSet)
        }
        if(training.realResults.isNotEmpty()) {
            Text("Real Force results · "+training.resultMethod.replaceFirstChar(Char::uppercase),style=MaterialTheme.typography.titleMedium)
            training.realResults.forEach {result ->
                Text("Rep "+result.rep+": "+displayNumber(result.pounds*(if(useLbs) 1.0 else 0.45359237))+" "+(if(useLbs) "lb" else "kg")+" · "+result.seconds+" s"+(if(result.rep==1) " · set weight" else " · display only"))
            }
            Text("Only rep 1's weight is saved/imported, together with every rep's duration. Later weights are shown here only and disappear when you start another set or close the app.",style=MaterialTheme.typography.bodySmall)
        }
        if(training.error.isNotBlank()) Text(training.error,color=MaterialTheme.colorScheme.error)
    }
}
}
