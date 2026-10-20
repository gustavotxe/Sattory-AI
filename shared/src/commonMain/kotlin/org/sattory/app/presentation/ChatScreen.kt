package org.sattory.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.sattory.app.ui.theme.SattoryTheme

@Composable
fun ChatScreen(factory: ChatStoreFactory) {
    val scope = rememberCoroutineScope()
    val store = remember(factory, scope) { factory.create(scope) }
    DisposableEffect(store) {
        onDispose { store.close() }
    }
    val state by store.state.collectAsState()

    SattoryTheme(darkTheme = state.themeMode == ThemeMode.Dark) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 800.dp) {
                Row(Modifier.fillMaxSize()) {
                    DesktopNavigation(
                        selected = state.selectedDestination,
                        modelStatus = state.modelStatus,
                        expanded = state.desktopNavigationExpanded,
                        onSelect = { store.dispatch(ChatIntent.DestinationSelected(it)) },
                        onNewChat = { store.dispatch(ChatIntent.NewConversation) },
                        onRetryModel = { store.dispatch(ChatIntent.RetryModelDownload) },
                        onToggleExpanded = { store.dispatch(ChatIntent.ToggleDesktopNavigation) },
                    )
                    ChatScaffold(
                        state = state,
                        onIntent = store::dispatch,
                        expanded = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                MobileChatLayout(state, store::dispatch)
            }
        }
    }
}