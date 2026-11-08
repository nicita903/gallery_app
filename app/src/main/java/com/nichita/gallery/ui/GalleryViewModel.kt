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

enum class GalleryTab { Photos, Albums }
data class GalleryUiState(
    val tab: GalleryTab = GalleryTab.Photos,
    val library: MediaLibrary = MediaLibrary(),
    val access: MediaAccess = MediaAccess(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val columns: Int = 3
)

class GalleryViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)
    private val mutableState = MutableStateFlow(GalleryUiState(tab =
        GalleryTab.entries.find { it.name == savedState.get<String>("tab") } ?: GalleryTab.Photos))
    private val deletedIds = MutableStateFlow<Set<String>>(emptySet())
    val state = mutableState.asStateFlow()
    private var observing: Job? = null
    private var loading: Job? = null
    private var pendingRefresh = false
    private var pendingForce = false
    private var loadGeneration = 0L
    private var columnsChanged = false
    private val columnWrites = Channel<Int>(Channel.CONFLATED)

    init {
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

    fun setColumns(columns: Int) {
        val value = columns.coerceIn(2, 8)
        if (value == state.value.columns) return
        columnsChanged = true
        mutableState.update { it.copy(columns = value) }
        columnWrites.trySend(value)
    }

    @OptIn(FlowPreview::class)
    fun start() {
        if (observing != null) {
            if (MediaPermissions.read(getApplication()) != state.value.access) refresh(force = true)
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
        if (loading?.isActive == true && access == state.value.access) {
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
        val before = state.value.library
        viewModelScope.launch {
            val filtered = withContext(Dispatchers.Default) { buildLibrary(before.items.filterNot { it.id in deletedIds.value }) }
            if (state.value.library === before) mutableState.update { it.copy(library = filtered) }
            refresh(force = true)
        }
    }
}
