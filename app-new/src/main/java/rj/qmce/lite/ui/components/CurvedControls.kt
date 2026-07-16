package rj.qmce.lite.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedAlignment
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedScope
import androidx.wear.compose.foundation.curvedComposable
import androidx.wear.compose.material3.SurfaceTransformation

// TODO & fixme: not finished curved components

@Composable
fun CurvedCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    edgePadding: Dp = 8.dp,
    cardThickness: Dp = 64.dp,
    anchor: Float = 90f,
    content: CurvedScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCurvedCardBackground(
                color = colors.containerColor,
                edgePadding = edgePadding.toPx(),
                cardThickness = cardThickness.toPx(),
                startAngle = anchor - 45f,
                sweepAngle = 90f,
            )
        }
        CurvedLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(edgePadding),
            anchor = anchor,
            anchorType = AnchorType.Center,
            radialAlignment = CurvedAlignment.Radial.Outer,
            contentBuilder = content,
        )
    }
}

private fun DrawScope.drawCurvedCardBackground(
    color: androidx.compose.ui.graphics.Color,
    edgePadding: Float,
    cardThickness: Float,
    startAngle: Float,
    sweepAngle: Float,
) {
    val diameter = size.minDimension - edgePadding * 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = (diameter - cardThickness) / 2f
    if (radius <= 0f || cardThickness <= 0f) return
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = cardThickness, cap = StrokeCap.Round),
    )
}

fun CurvedScope.curvedCompactButton(
    onClick: () -> Unit,
    modifier: CurvedModifier = CurvedModifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    icon: (@Composable BoxScope.() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = CompactButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    transformation: SurfaceTransformation? = null,
    label: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    rotationLocked: Boolean = false,
    radialAlignment: CurvedAlignment.Radial = CurvedAlignment.Radial.Outer,
) {
    curvedComposable(
        modifier = modifier,
        radialAlignment = radialAlignment,
        rotationLocked = rotationLocked,
    ) {
        CompactButton(
            onClick = onClick,
            modifier = Modifier,
            onLongClick = onLongClick,
            onLongClickLabel = onLongClickLabel,
            icon = icon,
            enabled = enabled,
            shape = shape ?: CompactButtonDefaults.shape,
            colors = colors ?: ButtonDefaults.buttonColors(),
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            transformation = transformation,
            label = label,
        )
    }
}
