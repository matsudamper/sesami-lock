package net.matsudamper.sesamilock

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import net.matsudamper.sesamilock.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val graph = (application as SesameLockApplication).graph
        val blePermissions = BlePermissions(applicationContext)
        setContent {
            MaterialTheme {
                AppRoot(graph = graph, blePermissions = blePermissions)
            }
        }
    }
}
