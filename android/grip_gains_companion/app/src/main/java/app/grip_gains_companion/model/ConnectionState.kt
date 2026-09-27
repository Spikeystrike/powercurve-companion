package app.grip_gains_companion.model

/**
 * Connection state for the Tindeq Progressor
 */
sealed class ConnectionState {
    data object Initializing : ConnectionState()
    data object Disconnected : ConnectionState()
    data object Scanning : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data object Reconnecting : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    
    val displayText: String
        get() = when (this) {
            is Initializing -> "Initializing..."
            is Disconnected -> "Disconnected"
            is Scanning -> "Searching for scale…"
            is Connecting -> "Connecting · waiting for readings…"
            is Connected -> "Connected"
            is Reconnecting -> "Waiting for scale readings…"
            is Error -> "Error: $message"
        }
}
