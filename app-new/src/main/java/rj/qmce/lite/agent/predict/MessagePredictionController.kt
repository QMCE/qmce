package rj.qmce.lite.agent.predict

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rj.qmce.lite.agent.AgentSubsystem

/**
 * State holder for the message-prediction feature (mirrors OtaUpdateSession's
 * ui-state pattern). A one-shot query that does NOT touch AgentSession history.
 */
object MessagePredictionController {

    private val _state = MutableStateFlow<PredictionUiState>(PredictionUiState.Idle)
    val state: StateFlow<PredictionUiState> = _state.asStateFlow()

    /** Kick off prediction. No-op if already Loading or Ready (re-entry guard). */
    fun start(peerUid: String, chatType: Int, initial: List<PredictionMessage>) {
        val current = _state.value
        if (current is PredictionUiState.Loading || current is PredictionUiState.Ready) return
        if (peerUid.isBlank() || chatType == 100) return
        _state.value = PredictionUiState.Loading
        AgentSubsystem.scope().launch {
            _state.value = MessagePredictionEngine.predict(peerUid, chatType, initial)
        }
    }

    fun reset() {
        _state.value = PredictionUiState.Idle
    }
}
