package net.matsudamper.sesamilock.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun HoldToActivateButton(
    text: String,
    icon: Painter,
    enabled: Boolean,
    holdMillis: Int,
    containerColor: Color,
    contentColor: Color,
    minHeight: Dp,
    onActivate: () -> Unit,
    onTapWithoutHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val activate = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onActivate()
    }
    val background = if (enabled) containerColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val foreground = if (enabled) contentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(background)
            .drawBehind {
                drawRect(
                    color = contentColor.copy(alpha = 0.35f),
                    size = Size(width = size.width * progress.value, height = size.height),
                )
            }
            .semantics {
                role = Role.Button
                if (enabled) {
                    onClick {
                        onTapWithoutHold()
                        true
                    }
                    onLongClick(label = text) {
                        activate()
                        true
                    }
                }
            }
            .pointerInput(enabled, holdMillis, onActivate, onTapWithoutHold) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    val animation = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(durationMillis = holdMillis, easing = LinearEasing))
                    }
                    val releasedBeforeHold = withTimeoutOrNull(holdMillis.toLong()) {
                        waitForUpOrCancellation() != null
                    }
                    animation.cancel()
                    when (releasedBeforeHold) {
                        null -> {
                            activate()
                            waitForUpOrCancellation()
                        }

                        true -> onTapWithoutHold()

                        false -> Unit
                    }
                    scope.launch { progress.animateTo(0f, tween(durationMillis = 150)) }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = icon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
        Text(text = text, color = foreground, style = MaterialTheme.typography.titleMedium)
    }
}
