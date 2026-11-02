package net.matsudamper.sesamilock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal data class LockStatusColors(
    val locked: Color,
    val lockedContainer: Color,
    val unlocked: Color,
    val unlockedContainer: Color,
)

private val lightStatusColors = LockStatusColors(
    locked = Color(0xFF2E7D32),
    lockedContainer = Color(0xFFDCEFD9),
    unlocked = Color(0xFFB45309),
    unlockedContainer = Color(0xFFFCE7CF),
)

private val darkStatusColors = LockStatusColors(
    locked = Color(0xFF8BD18F),
    lockedContainer = Color(0xFF1F3A21),
    unlocked = Color(0xFFF5B66B),
    unlockedContainer = Color(0xFF4A2E0F),
)

@Composable
internal fun lockStatusColors(): LockStatusColors = if (isSystemInDarkTheme()) darkStatusColors else lightStatusColors
