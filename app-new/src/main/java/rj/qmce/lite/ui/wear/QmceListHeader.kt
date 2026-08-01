package rj.qmce.lite.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text

/** Centered Wear Material3 [ListHeader] for TLC section titles. */
@Composable
fun QmceListHeader(
    text: String,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
) {
    ListHeader(
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
