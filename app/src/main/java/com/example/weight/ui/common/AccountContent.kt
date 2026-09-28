package com.example.weight.ui.common

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.sync.SyncRepository
import com.example.weight.data.scale.ScaleBleEngine
import com.example.weight.data.diet.DietDeleteUndoManager
import org.koin.compose.koinInject

/** Recreate navigation, drafts and ViewModels together at an account boundary. */
@Composable
fun AccountContent(content: @Composable () -> Unit) {
    val sync: SyncRepository = koinInject()
    val scale: ScaleBleEngine = koinInject()
    val undo: DietDeleteUndoManager = koinInject()
    val account by sync.account.collectAsStateWithLifecycle()
    key(account.userId) {
        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear(); scale.stopSession(); undo.commitPending() } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
    }
}
