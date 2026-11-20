package app.jellylauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.jellylauncher.data.AppsRepository
import app.jellylauncher.data.AuthExpiredException
import app.jellylauncher.data.JellyfinApi
import app.jellylauncher.data.LaunchableApp
import app.jellylauncher.data.Prefs
import app.jellylauncher.data.RatingsRepository
import app.jellylauncher.data.Scores
import app.jellylauncher.data.UpdateInfo
import app.jellylauncher.data.UpdateManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.jellylauncher.data.ResumeCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val showLogin: Boolean = false,
    val loggingIn: Boolean = false,
    val showAllApps: Boolean = false,
    val showSetup: Boolean = false,
    val overrideOn: Boolean = false,
    val loginError: String? = null,
    val cards: List<ResumeCard> = emptyList(),
    val apps: List<LaunchableApp> = emptyList(),
    val error: String? = null,
    val userName: String = "",
    val serverUrl: String = "",
    val ratingsKey: String = "",
    val scores: Map<String, Scores> = emptyMap(),
    val update: UpdateInfo? = null,
    val updateStatus: String? = null,
    val pinned: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    val visibleApps: List<LaunchableApp> get() = apps.filter { it.packageName !in hidden }
    val hiddenApps: List<LaunchableApp> get() = apps.filter { it.packageName in hidden }

    /** Dock: your pinned apps in your order, or the first few apps until you pin something. */
    val dockApps: List<LaunchableApp>
        get() = (if (pinned.isEmpty()) visibleApps.take(6)
        else pinned.mapNotNull { p -> visibleApps.find { it.packageName == p } }).take(8)
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val api = JellyfinApi(prefs)
    private val appsRepository = AppsRepository(application)

    private val layoutPrefs = application.getSharedPreferences("layout", 0)
    private val cachePrefs = application.getSharedPreferences("cache", 0)
    private val cacheJson = Json { ignoreUnknownKeys = true }
    private val updater = UpdateManager(application)
    private val ratings = RatingsRepository(application, prefs)
    private var lastUpdateCheck = 0L

    /** The last list we showed, so the home screen appears instantly and works when the server is asleep. */
    private fun loadCache(): List<ResumeCard> = try {
        cacheJson.decodeFromString<List<ResumeCard>>(cachePrefs.getString("cards", "[]").orEmpty())
    } catch (e: Exception) {
        emptyList()
    }

    private fun saveCache(cards: List<ResumeCard>) {
        try { cachePrefs.edit().putString("cards", cacheJson.encodeToString(cards)).apply() } catch (e: Exception) { }
    }

    private val _state = MutableStateFlow(
        HomeUiState(
            showLogin = !prefs.isLoggedIn,
            cards = if (prefs.isLoggedIn) loadCache() else emptyList(),
            loading = prefs.isLoggedIn && loadCache().isEmpty(),
            userName = prefs.userName,
            serverUrl = prefs.serverUrl,
            ratingsKey = prefs.ratingsKey,
            pinned = loadList("pinned"),
            hidden = loadList("hidden").toSet(),
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private fun loadList(key: String): List<String> =
        layoutPrefs.getString(key, "").orEmpty().split(",").filter { it.isNotBlank() }

    private fun saveLayout(pinned: List<String>, hidden: Set<String>) {
        layoutPrefs.edit().putString("pinned", pinned.joinToString(",")).putString("hidden", hidden.joinToString(",")).apply()
        _state.update { it.copy(pinned = pinned, hidden = hidden) }
    }

    /** Pin or unpin an app on the dock (max 8). */
    fun togglePin(pkg: String) {
        val s = _state.value
        val current = if (s.pinned.isEmpty()) s.dockApps.map { it.packageName } else s.pinned
        val next = if (pkg in current) current - pkg else if (current.size < 8) current + pkg else current
        saveLayout(next, s.hidden)
    }

    fun movePin(pkg: String, delta: Int) {
        val s = _state.value
        val list = (if (s.pinned.isEmpty()) s.dockApps.map { it.packageName } else s.pinned).toMutableList()
        val i = list.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        saveLayout(list, s.hidden)
    }

    /** Hide or show an app everywhere in Foyer (it stays installed). */
    fun toggleHide(pkg: String) {
        val s = _state.value
        if (pkg in s.hidden) saveLayout(s.pinned, s.hidden - pkg)
        else saveLayout((if (s.pinned.isEmpty()) s.dockApps.map { it.packageName } else s.pinned) - pkg, s.hidden + pkg)
    }

    private val setupPrefs = application.getSharedPreferences("setup", 0)

    init {
        if (!setupPrefs.getBoolean("seen", false)) _state.update { it.copy(showSetup = true, overrideOn = isOverrideOn()) }
    }

    private var refreshJob: Job? = null

    /** Reloads installed apps and Continue Watching. Called every time the home screen comes back. */
    fun refresh() {
        checkUpdate()
        viewModelScope.launch {
            try {
                val apps = appsRepository.load()
                _state.update { it.copy(apps = apps) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep whatever app list we already have.
            }
        }

        if (!prefs.isLoggedIn) {
            _state.update { it.copy(showLogin = true, loading = false) }
            return
        }

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val resume = api.continueWatching()
                val next = try { api.nextUp() } catch (e: AuthExpiredException) { throw e } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
                val latest = try { api.latest() } catch (e: AuthExpiredException) { throw e } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
                val seen = mutableSetOf<String>()
                val cards = (resume.map { api.toCard(it, "Continue watching") } +
                    next.map { api.toCard(it, "Next up") } +
                    latest.map { api.toCard(it, "Latest added") })
                    .filter { seen.add(it.id) }
                saveCache(cards)
                _state.update {
                    it.copy(
                        cards = cards,
                        loading = false,
                        error = null,
                        userName = prefs.userName,
                        serverUrl = prefs.serverUrl,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthExpiredException) {
                prefs.signOut()
                _state.update {
                    it.copy(
                        showLogin = true,
                        loading = false,
                        cards = emptyList(),
                        loginError = "Your session expired. Please sign in again.",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = if (it.cards.isNotEmpty()) null else "Can't reach your Jellyfin server (${e.message ?: e.javaClass.simpleName})",
                    )
                }
            }
        }
    }

    fun login(server: String, username: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(loggingIn = true, loginError = null) }
            try {
                api.login(server, username, password)
                _state.update {
                    it.copy(
                        loggingIn = false,
                        showLogin = false,
                        loading = true,
                        error = null,
                        userName = prefs.userName,
                        serverUrl = prefs.serverUrl,
                    )
                }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loggingIn = false,
                        loginError = e.message ?: "Couldn't sign in",
                    )
                }
            }
        }
    }

    fun openSettings() = _state.update { it.copy(showLogin = true, loginError = null) }

    fun openSetup() = _state.update { it.copy(showSetup = true, overrideOn = isOverrideOn()) }

    fun closeSetup() {
        setupPrefs.edit().putBoolean("seen", true).apply()
        _state.update { it.copy(showSetup = false) }
    }

    fun checkOverride() = _state.update { it.copy(overrideOn = isOverrideOn()) }

    private fun isOverrideOn(): Boolean {
        val app = getApplication<Application>()
        val enabled = android.provider.Settings.Secure.getString(
            app.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.contains(app.packageName + "/", ignoreCase = true) && enabled.contains("HomeOverrideService")
    }

    private val showImdb = mutableMapOf<String, String?>()

    /** Looks up IMDb and Rotten Tomatoes scores for the focused title (needs the optional MDBList key). */
    fun loadScores(card: ResumeCard) {
        if (prefs.ratingsKey.isBlank()) return
        if (_state.value.scores.containsKey(card.id)) return
        viewModelScope.launch {
            try {
                val seriesId = card.seriesId
                val found: String? = if (seriesId != null) {
                    if (!showImdb.containsKey(seriesId)) showImdb[seriesId] = api.imdbIdOf(seriesId)
                    showImdb[seriesId]
                } else {
                    card.imdbId
                }
                val imdbId = found ?: return@launch
                val s = ratings.get(imdbId, isShow = seriesId != null)
                if (s != null) _state.update { it.copy(scores = it.scores + (card.id to s)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    fun saveRatingsKey(key: String) {
        prefs.ratingsKey = key.trim()
        _state.update { it.copy(ratingsKey = prefs.ratingsKey, scores = emptyMap()) }
    }

    fun checkUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheck < 6 * 60 * 60 * 1000L) return
        lastUpdateCheck = now
        viewModelScope.launch {
            try {
                val info = updater.check()
                _state.update { it.copy(update = info) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastUpdateCheck = 0L
            }
        }
    }

    fun installUpdate() {
        val info = _state.value.update ?: return
        viewModelScope.launch {
            _state.update { it.copy(updateStatus = "Downloading update…") }
            try {
                updater.install(info)
                _state.update { it.copy(updateStatus = "Confirm the install on screen") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(updateStatus = "Update failed: ${e.message ?: "unknown error"}") }
            }
        }
    }

    fun checkUpdateNow() {
        lastUpdateCheck = 0L
        checkUpdate()
    }

    fun openAllApps() = _state.update { it.copy(showAllApps = true) }

    fun closeAllApps() = _state.update { it.copy(showAllApps = false) }

    fun closeSettings() {
        if (prefs.isLoggedIn) _state.update { it.copy(showLogin = false, loginError = null) }
    }
}
