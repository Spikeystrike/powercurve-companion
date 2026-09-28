package app.grip_gains_companion.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/** Release, scroll, lifecycle changes and timer phase changes cancel confirmation. */
@Composable
fun HoldToConfirm(label: String, enabled: Boolean = true, resetKey: Any, action: () -> Unit) {
    val progress=remember {Animatable(0f)}
    val currentAction by rememberUpdatedState(action)
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    Surface(color=if(enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape=MaterialTheme.shapes.medium,
        modifier=Modifier.fillMaxWidth().pointerInput(enabled,resetKey,lifecycleState) {
            if(enabled && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) detectTapGestures(onPress={
                coroutineScope {
                    val hold=launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f,tween(2000,easing=LinearEasing))
                        currentAction()
                    }
                    try {tryAwaitRelease()} finally {hold.cancel();progress.snapTo(0f)}
                }
            })
        }) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(label)
            Text(if(progress.value>0f) String.format(Locale.US,"Keep holding · %.1f s",(1f-progress.value)*2f) else "Hold for 2 seconds",style=MaterialTheme.typography.labelSmall)
            LinearProgressIndicator(progress={progress.value},modifier=Modifier.fillMaxWidth())
        }
    }
    LaunchedEffect(enabled,resetKey,lifecycleState) {progress.snapTo(0f)}
}
