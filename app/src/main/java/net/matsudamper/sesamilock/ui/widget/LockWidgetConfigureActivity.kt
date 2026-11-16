package net.matsudamper.sesamilock.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.matsudamper.sesamilock.MainActivity
import net.matsudamper.sesamilock.SesameLockApplication
import net.matsudamper.sesamilock.ui.theme.SesameTheme

internal class LockWidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as SesameLockApplication).graph
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (!graph.widgetController.isLockWidget(appWidgetId)) {
            finish()
            return
        }
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        setContent {
            SesameTheme {
                val viewModel = viewModel {
                    LockWidgetConfigureViewModel(
                        repository = graph.repository,
                        widgetController = graph.widgetController,
                        appWidgetId = appWidgetId,
                    )
                }
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                LockWidgetConfigureScreen(
                    uiState = uiState,
                    onDoneClick = {
                        setResult(RESULT_OK, result)
                        finish()
                    },
                    onOpenAppClick = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context, appWidgetId: Int): Intent = Intent(context, LockWidgetConfigureActivity::class.java)
            .setData(Uri.fromParts("sesamilock-widget", appWidgetId.toString(), null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
}
