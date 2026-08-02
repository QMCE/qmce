package rj.qmce.lite.agent.predict

/**
 * A single message in the prediction context window.
 *
 * @param sender display name ("我" for self, otherwise nickname/uid)
 * @param text   readable text (media -> "[图片]" etc., from UiMsg.text)
 * @param msgId  for loadOlder anchoring
 * @param msgTime seconds
 */
data class PredictionMessage(
    val sender: String,
    val text: String,
    val msgId: Long,
    val msgTime: Long,
)

sealed class PredictionUiState {
    data object Idle : PredictionUiState()
    data object Loading : PredictionUiState()
    data class Ready(val suggestions: List<String>) : PredictionUiState()
    data class Error(val message: String) : PredictionUiState()
}
