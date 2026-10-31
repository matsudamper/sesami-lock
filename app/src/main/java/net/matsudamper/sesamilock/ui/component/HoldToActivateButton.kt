package net.matsudamper.sesamilock.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun HoldToActivateButton(
    text: String,
    enabled: Boolean,
    holdMillis: Int,
    color: Color,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnActivate = rememberUpdatedState(onActivate)
    val contentColor = if (enabled) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .border(2.dp, contentColor, shape)
            .semantics {
                role = Role.Button
                if (enabled) {
                    onLongClick(label = text) {
                        currentOnActivate.value()
                        true
                    }
                }
            }
            .pointerInput(enabled, holdMillis) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    val animation = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(durationMillis = holdMillis, easing = LinearEasing))
                    }
                    val gestureEndedBeforeHold = withTimeoutOrNull(holdMillis.toLong()) {
                        waitForUpOrCancellation()
                        true
                    } ?: false
                    animation.cancel()
                    if (!gestureEndedBeforeHold) {
                        currentOnActivate.value()
                        waitForUpOrCancellation()
                    }
                    scope.launch { progress.snapTo(0f) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(contentColor.copy(alpha = 0.2f)),
        )
        Text(text = text, color = contentColor, style = MaterialTheme.typography.titleMedium)
    }
}
