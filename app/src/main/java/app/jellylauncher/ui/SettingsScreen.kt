package app.jellylauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    onServer: () -> Unit,
    onSetup: () -> Unit,
    onScreensaver: () -> Unit,
    onUpdate: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(Modifier.width(480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row0("Settings")
            Spacer(Modifier.height(4.dp))
            SettingsButton("Server sign-in and ratings key", onServer, first)
            SettingsButton("Make Foyer my home screen", onSetup)
            SettingsButton("Screensaver (show your backdrops)", onScreensaver)
            SettingsButton("Check for updates", onUpdate)
            SettingsButton("Back", onClose)
        }
    }
}

@Composable
private fun Row0(title: String) {
    Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    FocusCard(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        focusRequester = focusRequester,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize().background(CardBg), contentAlignment = Alignment.CenterStart) {
            Text(label, color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 18.dp))
        }
    }
}
