package com.nichita.gallery.ui

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.nichita.gallery.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class GalleryTab { Photos, Albums, Search, Settings }
enum class ThemeMode { Dark, Light, System }
data class GalleryUiState(
    val tab: GalleryTab = GalleryTab.Photos,
    val library: MediaLibrary = MediaLibrary(),
    val access: MediaAccess = MediaAccess(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val columns: Int = 3,
    val theme: ThemeMode = ThemeMode.Dark,
    val oldestFirst: Boolean = false,
    val albumNames: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val query: String = "",
    val searching: Boolean = false,
    val displayLibrary: MediaLibrary? = null,
    val allLibrary: MediaLibrary? = null,
    val searchLibrary: MediaLibrary = MediaLibrary()
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GalleryViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)
    private val mutableState = MutableStateFlow(GalleryUiState(tab =
        GalleryTab.entries.find { it.name == savedState.get<String>("tab") } ?: GalleryTab.Photos,
        query = savedState["query"] ?: "", favoritesOnly = savedState["favorites_only"] ?: false))
    private val deletedIds = MutableStateFlow<Set<String>>(emptySet())
    val theme = mutableState.map { it.theme }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.Dark)
    private data class Presentation(val library: MediaLibrary, val oldest: Boolean,
        val names: Boolean, val only: Boolean, val favorites: Set<String>)
    private data class Libraries(val input: Presentation, val all: MediaLibrary, val photos: MediaLibrary)
    private data class SearchResult(val source: MediaLibrary, val query: String, val library: MediaLibrary)
    private fun presentation(state: GalleryUiState) =
        Presentation(state.library, state.oldestFirst, state.albumNames, state.favoritesOnly, state.favorites)
    private val presented = mutableState.map(::presentation)
        .distinctUntilChanged().mapLatest { withContext(Dispatchers.Default) {
            val all = presentLibrary(it.library, it.oldest, it.names, false, it.favorites)
            Libraries(it, all, if (it.only) presentLibrary(it.library, it.oldest, it.names, true, it.favorites) else all)
        } }
    private var indexedLibrary: MediaLibrary? = null
    private var searchIndex: LocalSearchIndex? = null
    @OptIn(FlowPreview::class)
    private val results = combine(mutableState.map { it.library }.distinctUntilChanged(),
        mutableState.map { it.query }.distinctUntilChanged().debounce(220).onStart { emit("") }.distinctUntilChanged()) { library, query -> library to query }
        .mapLatest { (library, query) -> withContext(Dispatchers.Default) {
            val found = if (query.isBlank()) MediaLibrary() else {
                if (indexedLibrary !== library) {
                    searchIndex = LocalSearchIndex(library)
                    indexedLibrary = library
                }
                searchIndex!!.search(query)
            }
            SearchResult(library, query, found)
        } }
    val state = combine(mutableState, presented, results) { state, library, search ->
        // Publish a coherent snapshot: a finished refresh must not expose the previous empty grid.
        if (library.input != presentation(state) || search.source !== state.library) null
        else state.copy(displayLibrary = library.photos, allLibrary = library.all, searchLibrary = search.library,
            searching = search.query != state.query)
    }.filterNotNull().stateIn(viewModelScope, SharingStarted.Eagerly, mutableState.value)
    private var observing: Job? = null
    private var loading: Job? = null
    private var pendingRefresh = false
    private var pendingForce = false
    private var loadGeneration = 0L
    private var columnsChanged = false
    private var preferencesChanged = false
    private val columnWrites = Channel<Int>(Channel.CONFLATED)
    private val preferenceWrites = Channel<GalleryUiState>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            for (settings in preferenceWrites) withContext(Dispatchers.IO) {
                application.getSharedPreferences("gallery_settings", 0).edit {
                    putString("theme", settings.theme.name)
                    putBoolean("oldest", settings.oldestFirst)
                    putBoolean("album_names", settings.albumNames)
                    putStringSet("favorites", settings.favorites)
                }
            }
        }
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) {
                val prefs = application.getSharedPreferences("gallery_settings", 0)
                GalleryUiState(theme = ThemeMode.entries.find { it.name == prefs.getString("theme", null) } ?: ThemeMode.Dark,
                    oldestFirst = prefs.getBoolean("oldest", false), albumNames = prefs.getBoolean("album_names", false),
                    favorites = prefs.getStringSet("favorites", emptySet())!!.toSet())
            }
            if (!preferencesChanged) mutableState.update { it.copy(theme = settings.theme, oldestFirst = settings.oldestFirst,
                albumNames = settings.albumNames, favorites = settings.favorites) }
        }
        viewModelScope.launch {
            for (columns in columnWrites) withContext(Dispatchers.IO) {
                application.getSharedPreferences("gallery_settings", 0).edit { putInt("grid_columns", columns) }
            }
        }
        viewModelScope.launch {
            val columns = withContext(Dispatchers.IO) {
                application.getSharedPreferences("gallery_settings", 0).getInt("grid_columns", 3).coerceIn(2, 8)
            }
            if (!columnsChanged) mutableState.update { it.copy(columns = columns) }
        }
    }

    private fun settings(change: (GalleryUiState) -> GalleryUiState) {
        preferencesChanged = true
        mutableState.update(change)
        preferenceWrites.trySend(mutableState.value)
    }
    fun setTheme(theme: ThemeMode) = settings { it.copy(theme = theme) }
    fun setOldest(value: Boolean) = settings { it.copy(oldestFirst = value) }
    fun setAlbumNames(value: Boolean) = settings { it.copy(albumNames = value) }
    fun toggleFavorite(item: MediaItem) = settings { it.copy(favorites =
        if (item.id in it.favorites) it.favorites - item.id else it.favorites + item.id) }
    fun toggleFavorites() {
        mutableState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
        savedState["favorites_only"] = mutableState.value.favoritesOnly
    }
    fun search(query: String) {
        savedState["query"] = query
        mutableState.update { it.copy(query = query) }
    }

    fun setColumns(columns: Int) {
        val value = columns.coerceIn(2, 8)
        if (value == mutableState.value.columns) return
        columnsChanged = true
        mutableState.update { it.copy(columns = value) }
        columnWrites.trySend(value)
    }

    @OptIn(FlowPreview::class)
    fun start() {
        if (observing != null) {
            if (MediaPermissions.read(getApplication()) != mutableState.value.access) refresh(force = true)
            return
        }
        refresh()
        observing = viewModelScope.launch {
            repository.changes()
                .debounce(250)
                .catch { mutableState.update { it.copy(error = true, loading = false) } }
                .collect {
                    refresh()
                }
        }
    }

    fun stop() {
        loadGeneration++
        observing?.cancel()
        observing = null
        loading?.cancel()
        loading = null
        pendingRefresh = false
        pendingForce = false
    }

    fun refresh(force: Boolean = false) {
        val access = MediaPermissions.read(getApplication())
        if (loading?.isActive == true && access == mutableState.value.access) {
            pendingRefresh = true
            pendingForce = pendingForce || force
            return
        }
        val generation = ++loadGeneration
        pendingRefresh = false
        pendingForce = false
        loading?.cancel()
        mutableState.update { it.copy(access = access, loading = access.canRead,
            error = false, library = if (access != it.access || !access.canRead) MediaLibrary() else it.library) }
        if (!access.canRead) return
        loading = viewModelScope.launch {
            try {
                val loaded = repository.load(access, force)
                val library = withContext(Dispatchers.Default) {
                    val removed = deletedIds.value
                    if (removed.isEmpty()) loaded else buildLibrary(loaded.items.filterNot { it.id in removed })
                }
                mutableState.update { it.copy(library = library, loading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(library = MediaLibrary(), loading = false, error = true,
                    access = MediaPermissions.read(getApplication())) }
            } finally {
                if (generation == loadGeneration && pendingRefresh) {
                    val forceAgain = pendingForce
                    pendingRefresh = false
                    pendingForce = false
                    loading = null
                    refresh(force = forceAgain)
                }
            }
        }
    }

    fun selectTab(tab: GalleryTab) {
        savedState["tab"] = tab.name
        mutableState.update { it.copy(tab = tab) }
    }

    fun mediaDeleted(id: String) {
        deletedIds.update { it + id }
        val before = mutableState.value.library
        viewModelScope.launch {
            val filtered = withContext(Dispatchers.Default) { buildLibrary(before.items.filterNot { it.id in deletedIds.value }) }
            if (mutableState.value.library === before) mutableState.update { it.copy(library = filtered) }
            refresh(force = true)
        }
    }
}
