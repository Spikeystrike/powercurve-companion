package app.grip_gains_companion.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.grip_gains_companion.service.offline.OfflineTraining
import org.json.JSONObject

@Composable
fun PendingSetsDialog(training: OfflineTraining, useLbs: Boolean, close: () -> Unit) {
    val unit=if(useLbs) "lb" else "kg"
    val factor=if(useLbs) 1.0/0.45359237 else 1.0
    val revision by training.revision.collectAsState()
    val rows=remember(revision) {training.queue()}
    var selected by remember {mutableStateOf<JSONObject?>(null)}
    var deleting by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf("")}
    DisposableEffect(Unit) {onDispose {training.manageQueue(false)}}
    val row=selected
    if(row==null) {
        AlertDialog(onDismissRequest=close,title={Text("Pending sets")},text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Automatic uploads pause while this list is open.")
                if(rows.isEmpty()) Text("No pending sets.")
                rows.forEach {item->
                    Text(item.optString("gripper")+" · "+item.optString("side")+" · "+displayNumber(item.optDouble("weightKg")*factor)+" "+unit)
                    Text(item.optString("date_time")+" · "+item.optJSONArray("reps").toString()+" s",style=MaterialTheme.typography.bodySmall)
                    if(item.optBoolean("uploadStarted")) Text("Upload already started. Wait for sync, then edit or delete online.",style=MaterialTheme.typography.bodySmall)
                    else TextButton(onClick={selected=JSONObject(item.toString());message=""}) {Text("Edit or delete")}
                    HorizontalDivider()
                }
            }
        },confirmButton={TextButton(onClick=close){Text("Done")}})
    } else {
        var gripper by remember(row) {mutableStateOf(row.getString("gripper"))}
        var side by remember(row) {mutableStateOf(row.getString("side"))}
        val originalWeight=displayNumber(row.getDouble("weightKg")*factor)
        var weight by remember(row,useLbs) {mutableStateOf(originalWeight)}
        var reps by remember(row) {mutableStateOf((0 until row.getJSONArray("reps").length()).joinToString(", "){row.getJSONArray("reps").getInt(it).toString()})}
        val durations=reps.split(',').map {it.trim().toIntOrNull()}
        val kg=if(weight==originalWeight) row.getDouble("weightKg") else weight.replace(',','.').toDoubleOrNull()?.div(factor)
        val valid=kg!=null && kg.isFinite() && kg>0 && durations.size in 1..100 && durations.all {it!=null && it in 1..3600}
        AlertDialog(onDismissRequest={selected=null},title={Text("Edit pending set")},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row {listOf("micro","crusher","prime").forEach {name->FilterChip(selected=gripper==name,onClick={gripper=name},label={Text(name)})}}
                Row {listOf("left","right").forEach {name->FilterChip(selected=side==name,onClick={side=name},label={Text(name)})}}
                OutlinedTextField(weight,{weight=decimalInput(it)},label={Text("Set weight ("+unit+")")},singleLine=true)
                OutlinedTextField(reps,{reps=it},label={Text("Rep durations (seconds, comma-separated)")})
                Text("Each rep must be 1–3600 seconds. The original set date is preserved.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={deleting=true}) {Text("Delete set")}
                if(message.isNotEmpty()) Text(message,color=MaterialTheme.colorScheme.error)
            }
        },confirmButton={TextButton(enabled=valid,onClick={
            if(training.updatePending(row.getString("id"),gripper,side,kg!!,durations.filterNotNull())) selected=null
            else message="Could not update this set. It may already be uploading."
        }){Text("Save changes")}},dismissButton={TextButton(onClick={selected=null}){Text("Cancel")}})
        if(deleting) AlertDialog(onDismissRequest={deleting=false},title={Text("Delete this pending set?")},text={Text("This removes the local set without uploading it.")},confirmButton={TextButton(onClick={
            if(training.deletePending(row.getString("id"))) {deleting=false;selected=null}
            else {deleting=false;message="Could not delete this set. It may already be uploading."}
        }){Text("Delete")}},dismissButton={TextButton(onClick={deleting=false}){Text("Cancel")}})
    }
}
