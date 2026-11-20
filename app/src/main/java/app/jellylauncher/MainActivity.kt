package app.jellylauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.jellylauncher.data.WHOLPHIN_PACKAGE
import app.jellylauncher.data.LaunchableApp
import app.jellylauncher.data.ResumeCard
import app.jellylauncher.ui.AllAppsScreen
import app.jellylauncher.ui.HomeScreen
import app.jellylauncher.ui.JellyTheme
import app.jellylauncher.ui.LoginScreen
import app.jellylauncher.ui.SetupScreen
import app.jellylauncher.ui.SettingsScreen
import android.provider.Settings

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            JellyTheme {
                val state by viewModel.state.collectAsState()
                var showMenu by remember { mutableStateOf(false) }

                if (state.showLogin) {
                    LoginScreen(
                        initialServer = state.serverUrl,
                        initialRatingsKey = state.ratingsKey,
                        onSaveKey = viewModel::saveRatingsKey,
                        error = state.loginError,
                        busy = state.loggingIn,
                        canCancel = state.serverUrl.isNotEmpty() && !state.loading,
                        onSubmit = viewModel::login,
                        onCancel = viewModel::closeSettings,
                    )
                } else if (state.showSetup) {
                    SetupScreen(
                        overrideOn = state.overrideOn,
                        onRequestHome = ::requestHomeRole,
                        onAccessibility = { openSettingsScreen(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                        onAppInfo = { openSettingsScreen(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) },
                        onHomeSettings = { openSettingsScreen(Settings.ACTION_HOME_SETTINGS) },
                        onDone = viewModel::closeSetup,
                    )
                } else if (showMenu) {
                    SettingsScreen(
                        onServer = { showMenu = false; viewModel.openSettings() },
                        onSetup = { showMenu = false; viewModel.openSetup() },
                        onScreensaver = { openSettingsScreen(Settings.ACTION_DREAM_SETTINGS) },
                        onUpdate = { viewModel.checkUpdateNow(); showMenu = false },
                        onClose = { showMenu = false },
                    )
                } else if (state.showAllApps) {
                    AllAppsScreen(
                        visibleApps = state.visibleApps,
                        hiddenApps = state.hiddenApps,
                        pinned = (if (state.pinned.isEmpty()) state.dockApps.map { it.packageName } else state.pinned),
                        onLaunch = ::launchApp,
                        onTogglePin = viewModel::togglePin,
                        onMovePin = viewModel::movePin,
                        onToggleHide = viewModel::toggleHide,
                        onClose = viewModel::closeAllApps,
                    )
                } else {
                    HomeScreen(
                        state = state,
                        onPlay = ::openInWholphin,
                        onLaunchApp = ::launchApp,
                        onAllApps = viewModel::openAllApps,
                        onSettings = { showMenu = true },
                        onSetup = viewModel::openSetup,
                        onUpdate = viewModel::installUpdate,
                        onHeroChanged = viewModel::loadScores,
                        onRetry = viewModel::refresh,
                    )
                }
            }
        }
    }

    // Home was pressed (or we returned from another app): refresh Continue Watching and the app list.
    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        viewModel.checkOverride()
    }

    // Pressing Home while the launcher is already open brings you back to the main screen.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.closeAllApps()
    }

    /**
     * Plays the item in Wholphin. Uses Wholphin's documented link: wholphin://play?itemId=<id>&position=<ms>
     * (position resumes where you left off). If that fails, Wholphin is simply opened.
     */
    private fun openInWholphin(card: ResumeCard) {
        // Wholphin treats VIEW as "open the details page"; its PLAYBACK action starts playing.
        val direct = Intent("com.github.damontecres.wholphin.PLAYBACK").apply {
            data = Uri.parse("wholphin://play?itemId=${card.id}&position=${card.positionMs}")
            putExtra("itemId", card.id)
            putExtra("position", card.positionMs)
            setPackage(WHOLPHIN_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(direct)
            return
        } catch (e: ActivityNotFoundException) {
        } catch (e: SecurityException) {
        }

        val fallback = packageManager.getLeanbackLaunchIntentForPackage(WHOLPHIN_PACKAGE)
            ?: packageManager.getLaunchIntentForPackage(WHOLPHIN_PACKAGE)
        if (fallback == null) {
            Toast.makeText(this, "Install Wholphin to play this", Toast.LENGTH_LONG).show()
            return
        }
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(fallback)
    }

    /** Asks Android to make Foyer the Home app. Many Google TVs ignore this, which is why the other steps exist. */
    private fun requestHomeRole() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(android.app.role.RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 42)
                    return
                }
            }
        } catch (e: Exception) {
        }
        Toast.makeText(this, "This TV doesn't allow that. Use the Accessibility step.", Toast.LENGTH_LONG).show()
    }

    private fun openSettingsScreen(action: String, withPackage: Boolean = false) {
        val i = Intent(action).apply {
            if (withPackage) data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { startActivity(i) } catch (e: Exception) {
            Toast.makeText(this, "This TV doesn't have that settings page", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchApp(app: LaunchableApp) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.closeAllApps()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
