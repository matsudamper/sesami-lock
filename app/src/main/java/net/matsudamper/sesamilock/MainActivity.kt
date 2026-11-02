package net.matsudamper.sesamilock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.matsudamper.sesamilock.ui.AppRoot
import net.matsudamper.sesamilock.ui.theme.SesameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as SesameLockApplication).graph
        val blePermissions = BlePermissions(applicationContext)
        setContent {
            SesameTheme {
                AppRoot(graph = graph, blePermissions = blePermissions)
            }
        }
    }
}
