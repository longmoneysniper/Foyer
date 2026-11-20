package app.jellylauncher.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jellylauncher.HomeUiState
import app.jellylauncher.data.LaunchableApp
import app.jellylauncher.data.ResumeCard
import app.jellylauncher.data.Scores
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date

private val Gold = Color(0xFFE50914) // accent: Netflix-style red

@Composable
fun HomeScreen(
    state: HomeUiState,
    onPlay: (ResumeCard) -> Unit,
    onLaunchApp: (LaunchableApp) -> Unit,
    onAllApps: () -> Unit,
    onSettings: () -> Unit,
    onSetup: () -> Unit,
    onUpdate: () -> Unit,
    onHeroChanged: (ResumeCard) -> Unit,
    onRetry: () -> Unit,
) {
    // This is the home screen: Back should never leave it.
    BackHandler {}
    val context = LocalContext.current

    // Size things from the screen height so the layout fits any TV.
    val u = (LocalConfiguration.current.screenHeightDp / 540f).coerceIn(0.6f, 1.8f)
    val posterHeight = (176 * u).dp
    val posterWidth = (117 * u).dp
    val dockTile = (44 * u).dp

    var focusedId by remember { mutableStateOf<String?>(null) }
    var dockLabel by remember { mutableStateOf("") }
    // Only Continue watching is shown.
    val watching = state.cards.filter { it.section == "Continue watching" }
    val hero = watching.firstOrNull { it.id == focusedId } ?: watching.firstOrNull()

    // Fetch IMDb / Rotten Tomatoes scores for whatever is in the hero (after a short pause).
    LaunchedEffect(hero?.id) {
        val h = hero
        if (h != null) {
            delay(350)
            onHeroChanged(h)
        }
    }

    // Warm the image cache so the first frame after a start looks finished.
    LaunchedEffect(state.cards) {
        for (c in state.cards.take(4)) {
            val url = c.heroImage ?: continue
            context.imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
        }
    }

    // Rows: Continue watching / Next up / Latest added, switched with the chips above the posters.
    var selected by remember { mutableStateOf("Continue watching") }
    val sections = listOf("Continue watching")
    val active = if (selected in sections) selected else sections.firstOrNull() ?: "Continue watching"
    val stripCards = watching

    // Slow zoom on the backdrop so the screen feels alive.
    val zoom by rememberInfiniteTransition(label = "kb").animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Reverse),
        label = "zoom",
    )

    // Put focus on the newest Continue Watching item whenever the list's first entry changes.
    val firstCardFocus = remember { FocusRequester() }
    val firstId = watching.firstOrNull()?.id
    LaunchedEffect(firstId) {
        if (firstId != null) {
            delay(150)
            runCatching { firstCardFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        // Backdrop of the focused item, dimmed so text and icons stay readable.
        Crossfade(
            targetState = hero?.heroImage,
            animationSpec = tween(800),
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
            label = "hero",
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Bg.copy(alpha = 0.95f),
                    0.45f to Bg.copy(alpha = 0.78f),
                    1f to Bg.copy(alpha = 0.40f),
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Bg,
                )
            )
        )

        Column(Modifier.fillMaxSize().padding(top = 20.dp, bottom = 16.dp)) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(1.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.update != null) {
                        FocusCard(
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(16.dp),
                            onFocused = { dockLabel = "" },
                            onClick = onUpdate,
                        ) {
                            Box(
                                Modifier.background(Color(0xFFB20710)).padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    state.updateStatus ?: "Update available · build ${state.update.build}",
                                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                    Clock()
                }
            }

            // Main area: details on the left, posters on the right
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(start = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(0.38f).padding(end = 24.dp)) {
                    if (hero != null) HeroDetails(hero, u, state.scores[hero.id])
                }
                Column(Modifier.weight(0.62f)) {
                    if (sections.size > 1) {
                        Row(
                            Modifier.padding(start = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (s in sections) {
                                SectionChip(
                                    label = s,
                                    selected = s == active,
                                    onFocused = {
                                        selected = s
                                        state.cards.firstOrNull { it.section == s }?.let { focusedId = it.id }
                                        dockLabel = ""
                                    },
                                )
                            }
                        }
                    } else {
                        Text(
                            active,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (stripCards.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(start = 12.dp, end = 48.dp, top = 18.dp, bottom = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(stripCards, key = { _, card -> card.id }) { index, card ->
                                PosterCard(
                                    card = card,
                                    width = posterWidth,
                                    height = posterHeight,
                                    focusRequester = if (index == 0) firstCardFocus else null,
                                    onFocused = {
                                        focusedId = card.id
                                        dockLabel = ""
                                    },
                                    onClick = { onPlay(card) },
                                )
                            }
                        }
                    } else {
                        RowMessage(state = state, height = posterHeight, onRetry = onRetry)
                    }
                }
            }

            // Name of the focused dock app
            Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
                Text(dockLabel, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
            }

            // Dock
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (app in state.dockApps) {
                        FocusCard(
                            modifier = Modifier.size(dockTile),
                            shape = RoundedCornerShape(12.dp),
                            onFocused = { dockLabel = app.label },
                            onClick = { onLaunchApp(app) },
                        ) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = app.label,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(2.dp),
                            )
                        }
                    }
                    FocusCard(
                        modifier = Modifier.size(dockTile),
                        shape = RoundedCornerShape(12.dp),
                        onFocused = { dockLabel = "All apps" },
                        onClick = onAllApps,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AllAppsGlyph()
                        }
                    }
                    FocusCard(
                        modifier = Modifier.size(dockTile),
                        shape = RoundedCornerShape(12.dp),
                        onFocused = { dockLabel = "Settings" },
                        onClick = onSettings,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            SettingsGlyph()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroDetails(hero: ResumeCard, u: Float, scores: Scores?) {
    var logoFailed by remember(hero.id) { mutableStateOf(false) }
    Column {
        if (hero.logo != null && !logoFailed) {
            // Title artwork from Jellyfin; falls back to the text title if it can't load.
            AsyncImage(
                model = hero.logo,
                contentDescription = hero.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onError = { logoFailed = true },
                modifier = Modifier.fillMaxWidth().height((90 * u).dp),
            )
        } else {
            Text(
                hero.title,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    lineHeight = 42.sp,
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 3f), 10f),
                ),
            )
        }
        if (hero.subtitle.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                hero.subtitle,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RatingsRow(hero, scores)
        val status = listOf(hero.resumeAt, hero.remaining).filter { it.isNotEmpty() }.joinToString(" · ")
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(status, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
        }
        if (hero.progress > 0f) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.width(170.dp).height(3.dp).background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(hero.progress.coerceIn(0f, 1f)).background(Gold))
            }
        }
    }
}

@Composable
private fun RatingsRow(card: ResumeCard, scores: Scores?) {
    val imdb = scores?.imdb
    val communityText = if (imdb == null) card.community?.let { String.format("%.1f", it) } else null
    val critics = scores?.critics ?: card.critic?.let { "$it%" }
    val audience = scores?.audience
    if (imdb == null && communityText == null && critics == null && audience == null) return

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        val star = imdb ?: communityText
        if (star != null) {
            ScoreBadge(if (imdb != null) "IMDb" else "Community", star) {
                RatingLogo(if (imdb != null) "rating_imdb" else "") { Text("★", color = Color(0xFFF5C518), fontSize = 18.sp) }
            }
        }
        if (critics != null) {
            val pct = critics.removeSuffix("%").toIntOrNull() ?: 100
            ScoreBadge(if (scores?.critics != null) "RT Critics" else "Critics", critics) {
                RatingLogo(if (scores?.critics != null) "rating_rt_critics" else "") {
                    Box(Modifier.size(16.dp).background(if (pct >= 60) Color(0xFFE5483C) else Color(0xFF6BBE45), CircleShape))
                }
            }
        }
        if (audience != null) {
            val pct = audience.removeSuffix("%").toIntOrNull() ?: 100
            ScoreBadge("RT Audience", audience) {
                RatingLogo("rating_rt_audience") {
                    Box(Modifier.size(16.dp).background(if (pct >= 60) Color(0xFFFFA726) else Color(0xFF8D8D8D), RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}

/**
 * Shows your own logo image if you added one to the app (res/drawable-nodpi/<name>.png),
 * otherwise the plain icon passed in as [fallback].
 */
@Composable
private fun RatingLogo(name: String, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    val id = remember(name) {
        if (name.isEmpty()) 0 else context.resources.getIdentifier(name, "drawable", context.packageName)
    }
    if (id != 0) {
        Image(
            painter = painterResource(id),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(24.dp).widthIn(max = 64.dp),
        )
    } else {
        fallback()
    }
}

@Composable
private fun ScoreBadge(label: String, score: String, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(5.dp))
        Column {
            Text(score, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SectionChip(label: String, selected: Boolean, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) Gold else Color.White.copy(alpha = 0.65f),
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun greeting(name: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val part = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isBlank()) part else "$part, $name"
}

@Composable
private fun RowMessage(state: HomeUiState, height: Dp, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(height).padding(start = 8.dp, end = 48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            state.error != null -> Column {
                Text(state.error, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
            state.loading -> Text("Loading…", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            else -> Text(
                "Nothing in progress. Start something in Jellyfin and it will show up here.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun PosterCard(
    card: ResumeCard,
    width: Dp,
    height: Dp,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    FocusCard(
        modifier = Modifier.width(width).height(height),
        focusRequester = focusRequester,
        shape = RoundedCornerShape(14.dp),
        onFocused = onFocused,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize().background(CardBg))
        val image = card.poster ?: card.cardImage
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                card.title,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(10.dp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (card.isNew) {
            Text(
                "NEW",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Gold, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** A focusable tile that grows and gets a gold outline when focused with the D-pad. */
@Composable
internal fun FocusCard(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "scale")

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick),
        content = content,
    )
}

@Composable
private fun Logo(greeting: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        JellyMark(Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "FOYER",
            color = Color(0xFFE50914),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            style = TextStyle(fontFamily = FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))),
        )
        Spacer(Modifier.width(14.dp))
        Text(greeting, color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp)
    }
}

/** The logo: a "resume" ring (a progress arc with a gold dot where you left off) around a play button. */
@Composable
fun JellyMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.width / 96f
        val stroke = 7f * s
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF16233A), Color(0xFF0A0F1A))),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f * s, 22f * s),
        )
        drawArc(
            brush = Brush.sweepGradient(listOf(Color(0xFF7B5CFF), Color(0xFF00A4DC), Color(0xFF4DE3C4), Color(0xFF7B5CFF))),
            startAngle = -90f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(20f * s, 20f * s),
            size = androidx.compose.ui.geometry.Size(56f * s, 56f * s),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        drawCircle(Color(0xFFFFC145), radius = 5.5f * s, center = Offset(20f * s, 48f * s))
        val tri = Path().apply {
            moveTo(42f * s, 36f * s); lineTo(64f * s, 48f * s); lineTo(42f * s, 60f * s); close()
        }
        drawPath(tri, Color.White)
        drawPath(tri, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f * s, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun AllAppsGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val cell = size.width / 3f
        for (row in 0..2) {
            for (col in 0..2) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = cell * 0.18f,
                    center = Offset(cell * (col + 0.5f), cell * (row + 0.5f)),
                )
            }
        }
    }
}

@Composable
private fun SettingsGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val ys = listOf(0.2f, 0.5f, 0.8f)
        val knobs = listOf(0.3f, 0.7f, 0.45f)
        ys.forEachIndexed { i, y ->
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(0f, size.height * y),
                end = Offset(size.width, size.height * y),
                strokeWidth = 3f,
            )
            drawCircle(
                color = Color.White,
                radius = size.width * 0.11f,
                center = Offset(size.width * knobs[i], size.height * y),
            )
        }
    }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val minute = now / 60_000
    val time = remember(minute) { DateFormat.getTimeFormat(context).format(Date(now)) }

    Text(time, color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
}
