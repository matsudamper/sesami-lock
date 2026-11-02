package net.matsudamper.sesamilock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val fallbackLightScheme = lightColorScheme(
    primary = Color(0xFF00696E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF0F5),
    onPrimaryContainer = Color(0xFF002022),
)

private val fallbackDarkScheme = darkColorScheme(
    primary = Color(0xFF80D4D9),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F53),
    onPrimaryContainer = Color(0xFF9CF0F5),
)

@Composable
internal fun SesameTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> fallbackDarkScheme
        else -> fallbackLightScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
