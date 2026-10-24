package dev.vitreo.launcher.update

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vitreo.launcher.ui.*

class UpdatesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { VitreoTheme { Surface { Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
            TextButton(onClick={ finish() }) { Text("Done") }
            Text("Software Update",style=MaterialTheme.typography.headlineLarge)
            UpdatesSettings()
        } } } }
    }
    override fun onResume() { super.onResume(); AppUpdates.onForeground() }
}
