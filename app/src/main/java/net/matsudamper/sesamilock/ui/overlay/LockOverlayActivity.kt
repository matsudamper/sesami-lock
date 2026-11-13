package net.matsudamper.sesamilock.ui.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.matsudamper.sesamilock.BlePermissions
import net.matsudamper.sesamilock.SesameLockApplication
import net.matsudamper.sesamilock.sesame.core.model.LockId
import net.matsudamper.sesamilock.sesame.core.model.LockRoute
import net.matsudamper.sesamilock.ui.home.HomeViewModel
import net.matsudamper.sesamilock.ui.theme.SesameTheme

internal class LockOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lockId = intent.getStringExtra(EXTRA_LOCK_ID)?.let(::parseLockId)
        if (lockId == null) {
            finish()
            return
        }
        val graph = (application as SesameLockApplication).graph
        val blePermissions = BlePermissions(applicationContext)
        setContent {
            SesameTheme {
                val viewModel = viewModel {
                    HomeViewModel(
                        repository = graph.repository,
                        transports = graph.transports,
                        coordinator = graph.coordinator,
                        clock = graph.clock,
                        registrySync = graph.registrySync,
                        blePermissions = blePermissions,
                        editLockSelection = graph.editLockSelection,
                        widgetController = graph.widgetController,
                        focusedLockId = lockId,
                    )
                }
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                LockOverlayScreen(uiState = uiState, onDismiss = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_LOCK_ID = "lock_id"

        fun createIntent(context: Context, lockIdValue: String): Intent = Intent(context, LockOverlayActivity::class.java)
            .setData(Uri.fromParts("sesamilock", lockIdValue, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_LOCK_ID, lockIdValue)

        private fun parseLockId(value: String): LockId? {
            val route = LockRoute.entries.firstOrNull { it.name == value.substringBefore(':') } ?: return null
            return LockId.of(value.substringAfter(':'), route)
        }
    }
}
