package dev.vitreo.launcher

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vitreo.launcher.ui.LauncherScreen
import dev.vitreo.launcher.ui.VitreoTheme

class MainActivity : ComponentActivity() {
    private val model by viewModels<LauncherViewModel>()
    private var homeSerial by mutableIntStateOf(0)
    private var defaultHome by mutableStateOf(false)
    private var widgetConfigurationResult: ((Boolean) -> Unit)? = null
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        defaultHome = isDefaultHome()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            val ui by model.ui.collectAsStateWithLifecycle()
            VitreoTheme(ui.layout.appearance.interfaceStyle) {
                LauncherScreen(model, homeSerial, defaultHome, ::chooseHome, ::launchApp, ::appInfo, ::configureWidget)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        defaultHome = isDefaultHome()
        model.refresh()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        homeSerial++
    }
    private fun isDefaultHome(): Boolean = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
    private fun chooseHome() {
        try {
            val role = getSystemService(RoleManager::class.java)
            if (role != null && role.isRoleAvailable(RoleManager.ROLE_HOME)) roleRequest.launch(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
            else startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) { model.message("Abre Ajustes de Android > Apps predeterminadas > App de inicio.") }
    }
    private fun launchApp(id: String) {
        // Only an activity from the live launchable-app catalog can be launched.
        if (model.ui.value.apps.none { it.id == id }) {
            model.message("La app no est\u00e1 disponible en este perfil. Puedes quitar su acceso directo."); return
        }
        val component = ComponentName.unflattenFromString(id) ?: return
        try {
            startActivity(Intent.makeMainActivity(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            model.recordLaunch(id)
        }
        catch (e: Exception) { model.message("Android no ha permitido abrir esta aplicaci\u00f3n.") }
    }
    private fun appInfo(id: String) {
        val app = model.ui.value.apps.find { it.id == id } ?: return
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))) }
        catch (e: Exception) { model.message("No se pudo abrir la informaci\u00f3n de la app.") }
    }
    private fun configureWidget(host: AppWidgetHost, id: Int, result: (Boolean) -> Unit) {
        widgetConfigurationResult = result
        try { host.startAppWidgetConfigureActivityForResult(this, id, 0, 19384, null) }
        catch (_: Exception) { widgetConfigurationResult = null; result(false) }
    }
    @Deprecated("Android widget configuration uses the host's activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 19384) {
            val callback = widgetConfigurationResult
            widgetConfigurationResult = null
            callback?.invoke(resultCode == RESULT_OK)
        } else super.onActivityResult(requestCode, resultCode, data)
    }
}
