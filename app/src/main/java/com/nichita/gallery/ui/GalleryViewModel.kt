package com.nichita.gallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nichita.gallery.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GalleryTab { Photos, Albums }
data class GalleryUiState(
    val tab: GalleryTab = GalleryTab.Photos,
    val library: MediaLibrary = MediaLibrary(),
    val access: MediaAccess = MediaAccess(),
    val loading: Boolean = true,
    val error: Boolean = false
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)
    private val mutableState = MutableStateFlow(GalleryUiState())
    val state = mutableState.asStateFlow()
    private var observing: Job? = null
    private var loading: Job? = null

    @OptIn(FlowPreview::class)
    fun start() {
        if (observing != null) return
        observing = viewModelScope.launch {
            repository.changes().debounce(300).collect { refresh() }
        }
    }

    fun stop() {
        observing?.cancel()
        observing = null
        loading?.cancel()
    }

    fun refresh() {
        loading?.cancel()
        val access = MediaPermissions.read(getApplication())
        mutableState.update { it.copy(access = access, loading = access.canRead,
            error = false, library = if (access != it.access || !access.canRead) MediaLibrary() else it.library) }
        if (!access.canRead) return
        loading = viewModelScope.launch {
            try {
                val library = repository.load(access)
                mutableState.update { it.copy(library = library, loading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(library = MediaLibrary(), loading = false, error = true,
                    access = MediaPermissions.read(getApplication())) }
            }
        }
    }

    fun selectTab(tab: GalleryTab) {
        mutableState.update { it.copy(tab = tab) }
    }
}
