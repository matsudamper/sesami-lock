package net.matsudamper.sesamilock.ui.component

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

@Stable
internal class ReorderState(
    private val listState: LazyListState,
    private val leadingItemCount: Int,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    private var draggingIndex by mutableStateOf<Int?>(null)
    private var dragOffset by mutableFloatStateOf(0f)

    fun dragModifier(index: Int): Modifier = Modifier.pointerInput(index) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                draggingIndex = index
                dragOffset = 0f
            },
            onDrag = { change, amount: Offset ->
                change.consume()
                dragOffset += amount.y
            },
            onDragEnd = { finish() },
            onDragCancel = { reset() },
        )
    }

    fun itemModifier(index: Int): Modifier {
        val translation = translationOf(index)
        return Modifier.graphicsLayer { translationY = translation }
    }

    private fun translationOf(index: Int): Float {
        val from = draggingIndex ?: return 0f
        if (index == from) return dragOffset
        val target = targetIndex(from) ?: return 0f
        val draggedStep = stepOf(from) ?: return 0f
        return when {
            from < target && index in (from + 1)..target -> -draggedStep
            from > target && index in target until from -> draggedStep
            else -> 0f
        }
    }

    private fun stepOf(index: Int): Float? {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index + leadingItemCount } ?: return null
        return (info.size + listState.layoutInfo.mainAxisItemSpacing).toFloat()
    }

    private fun targetIndex(from: Int): Int? {
        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.index == from + leadingItemCount } ?: return null
        val center = dragged.offset + dragged.size / 2f + dragOffset
        val hit = visible.firstOrNull { it.index >= leadingItemCount && center >= it.offset && center < it.offset + it.size }
        return when {
            hit != null -> hit.index - leadingItemCount
            center < dragged.offset -> visible.filter { it.index >= leadingItemCount }.minOfOrNull { it.index }?.minus(leadingItemCount)
            else -> visible.maxOfOrNull { it.index }?.minus(leadingItemCount)
        }
    }

    private fun finish() {
        val from = draggingIndex
        val target = from?.let { targetIndex(it) }
        reset()
        if (from != null && target != null && from != target) onMove(from, target)
    }

    private fun reset() {
        draggingIndex = null
        dragOffset = 0f
    }
}

@Composable
internal fun rememberReorderState(
    listState: LazyListState,
    leadingItemCount: Int,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): ReorderState {
    val latestOnMove by rememberUpdatedState(onMove)
    return remember(listState, leadingItemCount) {
        ReorderState(listState, leadingItemCount) { from, to -> latestOnMove(from, to) }
    }
}
