package rj.qmce.lite.notify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Signals MainActivity that a new notification deep-link intent is available. */
object QmceDeepLinks {
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick.asStateFlow()

    fun notifyNewIntent() {
        _tick.value = _tick.value + 1
    }
}
