package app.grip_gains_companion.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import app.grip_gains_companion.service.offline.OfflineCurve
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
fun OfflineStatus(training: OfflineTraining) {
    val revision by training.revision.collectAsState()
    @Suppress("UNUSED_VARIABLE") val refresh=revision
    if(training.pending==0 && training.synced==0 && training.error.isEmpty()) return
    Surface(color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal=12.dp,vertical=6.dp)) {
            if(training.pending>0) Text("${training.pending} set(s) waiting to sync",style=MaterialTheme.typography.labelLarge)
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
    val defaults=remember {training.state.optJSONObject("defaults")}
    var gripper by rememberSaveable {mutableStateOf(defaults?.optString("gripper") ?: "crusher")}
    var side by rememberSaveable {mutableStateOf(defaults?.optString("side") ?: "left")}
    var weight by rememberSaveable {mutableStateOf(defaults?.optDouble("weightKg")?.let {if(useLbs) it/0.45359237 else it}?.toString() ?: "")}
    var reps by rememberSaveable {mutableStateOf((defaults?.optInt("plannedReps") ?: 6).toString())}
    var rest by rememberSaveable {mutableStateOf((defaults?.optInt("rest") ?: 10).toString())}
    var countdown by rememberSaveable {mutableStateOf((defaults?.optInt("countdown") ?: 20).toString())}
    var previousLbs by rememberSaveable {mutableStateOf(useLbs)}
    LaunchedEffect(useLbs) {
        if(previousLbs!=useLbs) {
            weight.replace(',','.').toDoubleOrNull()?.let { weight=(if(useLbs) it/0.45359237 else it*0.45359237).toString() }
            previousLbs=useLbs
        }
    }
    val kg=weight.replace(',','.').toDoubleOrNull()?.let {if(useLbs)it*0.45359237 else it}
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Column(modifier.background(Color(0xFF1A2231)).verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(training.online) "Local set timer" else "Offline set timer",style=MaterialTheme.typography.titleLarge)
        val active=training.active
        if(active==null) {
            Text("Sets are saved on this phone and imported automatically when connected.",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("micro","crusher","prime").forEach {item->FilterChip(selected=gripper==item,onClick={gripper=item},label={Text(item.replaceFirstChar(Char::uppercase))})} }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("left","right").forEach {item->FilterChip(selected=side==item,onClick={side=item},label={Text(item.replaceFirstChar(Char::uppercase))})}}
            val curve=training.curve(gripper,side)
            OfflineCurvePanel(curve,training.curveSavedAt,useLbs,weight.replace(',','.').toDoubleOrNull()) { match ->
                weight=String.format(java.util.Locale.US,"%.2f",match.weight)
                reps=match.zone.reps.toString()
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(weight,{weight=it},label={Text("Weight (${if(useLbs) "lb" else "kg"})")},singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(reps,{reps=it},label={Text("Reps (1–100)")},singleLine=true,modifier=Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rest,{rest=it},label={Text("Rest (0–600 s)")},singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(countdown,{countdown=it},label={Text("Countdown (0–60 s)")},singleLine=true,modifier=Modifier.weight(1f))
            }
            Button(onClick={training.start(gripper,side,kg!!,reps.toInt(),rest.toInt(),countdown.toInt(),curve?.estimate(weight.replace(',','.').toDoubleOrNull() ?: 0.0,useLbs)?.seconds)},enabled=training.available && kg!=null && kg.isFinite() && kg>0 && reps.toIntOrNull() in 1..100 && rest.toIntOrNull() in 0..600 && countdown.toIntOrNull() in 0..60,modifier=Modifier.fillMaxWidth()){Text("Start set")}
            if(training.online) TextButton(onClick=training::useWebsite){Text("Return to Powercurve")}
        } else {
            val done=active.getJSONArray("reps").length()
            Text("${active.getString("gripper")} · ${active.getString("side")} · " + String.format(java.util.Locale.US,"%.1f %s",if(useLbs) active.getDouble("weightKg")/0.45359237 else active.getDouble("weightKg"),if(useLbs) "lb" else "kg"))
            Text("${training.phase.replaceFirstChar(Char::uppercase)} · ${training.seconds}s",style=MaterialTheme.typography.headlineLarge)
            if(active.optInt("targetDuration")>0) Text("Estimated hold: ${active.getInt("targetDuration")} s")
            Text("$done / ${active.getInt("plannedReps")} reps completed")
            if(training.phase=="paused") {
                Text("Interrupted set recovered. Completed reps are safe; the interrupted rep was not counted.")
                Button(onClick=training::resumeRecovered){Text("Continue set")}
            }
            if(training.phase=="rep") Button(onClick=training::endRep,modifier=Modifier.fillMaxWidth()){Text("End rep")}
            if(done>0 && training.phase!="rep") Button(onClick=training::finish){Text("Save set now")}
            if(done==0 && training.phase!="rep") TextButton(onClick=training::cancelEmpty){Text("Cancel empty set")}
        }
        if(training.error.isNotBlank()) Text(training.error,color=MaterialTheme.colorScheme.error)
    }
}
}
