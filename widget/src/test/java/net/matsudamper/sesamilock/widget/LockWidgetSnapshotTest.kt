package net.matsudamper.sesamilock.widget

import android.content.ComponentName
import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import kotlinx.coroutines.runBlocking
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(PaparazziTestCategory::class)
class LockWidgetSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = BaseDeviceConfig, renderingMode = SessionParams.RenderingMode.SHRINK)

    @Test
    fun snapshot() {
        val filter = System.getProperty("paparazzi.filter", "").orEmpty()
        snapshotCases()
            .filter { case -> filter.isEmpty() || case.name.contains(filter, ignoreCase = true) }
            .forEach { case -> paparazzi.snapshot(view = render(case), name = case.name) }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private fun render(case: SnapshotCase): ViewGroup {
        val context = paparazzi.context
        val remoteViews = runBlocking {
            GlanceRemoteViews().compose(context = context, size = case.size) { case.content() }.remoteViews
        }
        val density = context.resources.displayMetrics.density
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                (case.size.width.value * density).toInt(),
                (case.size.height.value * density).toInt(),
            )
            setBackgroundColor(WallpaperSampleColor)
            addView(remoteViews.apply(context, this))
        }
    }

    private fun snapshotCases(): List<SnapshotCase> = buildList {
        LockWidgetStyle.entries.forEach { style ->
            val styleName = style.name.lowercase()
            listOf("compact" to CompactSize, "wide" to WideSize, "wide_tall" to WideTallSize).forEach { (sizeName, size) ->
                LockStates.forEach { state ->
                    add(
                        SnapshotCase("${sizeName}_${state.name}_$styleName", size) {
                            LockContent(
                                style = style,
                                name = "玄関",
                                statusLabel = state.statusLabel,
                                detail = "確認 12:34:56",
                                icon = state.icon,
                                onClick = previewAction(),
                            )
                        },
                    )
                }
                add(
                    SnapshotCase("${sizeName}_unconfigured_$styleName", size) {
                        UnconfiguredContent(style = style, onClick = previewAction())
                    },
                )
            }
        }
    }

    private class SnapshotCase(val name: String, val size: DpSize, val content: @Composable () -> Unit)

    private class LockState(val name: String, val statusLabel: String, val icon: LockWidgetIcon)

    private companion object {
        val BaseDeviceConfig = DeviceConfig.PIXEL_5
        val CompactSize = DpSize(70.dp, 70.dp)
        val WideSize = DpSize(180.dp, 56.dp)
        val WideTallSize = DpSize(180.dp, 90.dp)
        val WallpaperSampleColor = Color.rgb(0x6B, 0x72, 0x80)
        val LockStates = listOf(
            LockState("locked", "施錠中", LockWidgetIcon.LOCKED),
            LockState("unlocked", "解錠中", LockWidgetIcon.UNLOCKED),
            LockState("in_progress", "接続しています…", LockWidgetIcon.MOVING),
            LockState("unknown", "状態不明", LockWidgetIcon.UNKNOWN),
        )

        fun previewAction(): Action = actionStartActivity(ComponentName("", ""))
    }
}
