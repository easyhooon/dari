package fixtures

import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

// Two suspension points with code after them, so Kotlin compiles a real state machine.
class SuspendBridge {
    @JvmField var pending: Continuation<Unit>? = null

    @JvmField val steps = mutableListOf<String>()

    suspend fun send(message: String, log: String): String {
        steps += "start $message"
        suspendCoroutine { pending = it }
        steps += "resumed once"
        suspendCoroutine { pending = it }
        steps += "resumed twice"
        return "$message $log"
    }
}
