package net.matsudamper.sesamilock

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.fragment.app.FragmentActivity
import net.matsudamper.sesamilock.ui.AppRoot

class MainActivity : FragmentActivity() {
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
