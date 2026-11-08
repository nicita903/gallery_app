package com.nichita.gallery.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.nichita.gallery.data.*
import kotlinx.coroutines.CancellationException

internal data class MediaDeleteAction(val busy: Boolean, val request: (MediaItem) -> Unit)

@Composable
internal fun rememberMediaDeleteAction(onDeleted: (String) -> Unit, onError: () -> Unit): MediaDeleteAction {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val service = remember(resolver) { MediaDeletion(resolver) }
    var id by rememberSaveable { mutableStateOf<String?>(null) }
    var uri by rememberSaveable { mutableStateOf<String?>(null) }
    var stage by rememberSaveable { mutableStateOf<String?>(null) }
    var systemDeletes by rememberSaveable { mutableStateOf(true) }
    val deletedCallback by rememberUpdatedState(onDeleted)
    val errorCallback by rememberUpdatedState(onError)
    fun clear() { stage = null; id = null; uri = null }
    fun completed() { id?.let(deletedCallback); clear() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) clear()
        else if (systemDeletes) completed()
        else stage = "retry"
    }
    LaunchedEffect(stage, uri) {
        val target = uri?.let(Uri::parse) ?: return@LaunchedEffect
        try {
            when (stage) {
                "prepare" -> when (val preparation = service.prepare(target)) {
                    DeletePreparation.Deleted -> completed()
                    is DeletePreparation.Confirmation -> {
                        systemDeletes = preparation.systemDeletes
                        stage = "system"
                        launcher.launch(IntentSenderRequest.Builder(preparation.intent.intentSender).build())
                    }
                }
                "retry" -> { service.retryAuthorized(target); completed() }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { clear(); errorCallback() }
    }
    return MediaDeleteAction(id != null) { item ->
        if (id == null) { id = item.id; uri = item.contentUri.toString(); stage = "prepare" }
    }
}
