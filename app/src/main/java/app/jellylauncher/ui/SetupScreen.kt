package app.jellylauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
fun SetupScreen(
    overrideOn: Boolean,
    onRequestHome: () -> Unit,
    onAccessibility: () -> Unit,
    onAppInfo: () -> Unit,
    onHomeSettings: () -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }

    Row(Modifier.fillMaxSize().background(Bg).padding(horizontal = 56.dp, vertical = 28.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            JellyMark(Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("Make this your home screen", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Google TV doesn't let apps replace Home directly, so try these in order:",
                color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text("1. Press \"Ask Android to make Foyer Home\". If a prompt appears, choose Foyer.", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("2. Otherwise open Accessibility and switch on \"Foyer home button\".", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("3. If that switch is greyed out or turns itself off, Android is blocking apps installed by hand. Open App info and look for \"Allow restricted settings\". If it isn't there, install Foyer once through its own Update button (it then counts as trusted), then try step 2 again.", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("4. Press Home on your remote. You should land here.", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                if (overrideOn) "Status: ON — Home button is taken over" else "Status: OFF — Home still opens the stock screen",
                color = if (overrideOn) Color(0xFF4DE3C4) else Color(0xFFFFC145),
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(40.dp))
        Column(Modifier.width(300.dp), verticalArrangement = Arrangement.Center) {
            SetupButton("Ask Android to make Foyer Home", onRequestHome, first)
            Spacer(Modifier.height(12.dp))
            SetupButton("Open Accessibility settings", onAccessibility)
            Spacer(Modifier.height(12.dp))
            SetupButton("Open App info (restricted settings)", onAppInfo)
            Spacer(Modifier.height(12.dp))
            SetupButton("Open Home app settings", onHomeSettings)
            Spacer(Modifier.height(12.dp))
            SetupButton("Done", onDone)
        }
    }
}

@Composable
private fun SetupButton(label: String, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    FocusCard(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        focusRequester = focusRequester,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxSize().background(CardBg), verticalArrangement = Arrangement.Center) {
            Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

