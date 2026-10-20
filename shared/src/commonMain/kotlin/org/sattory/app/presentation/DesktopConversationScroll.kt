package org.sattory.app.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun Modifier.desktopConversationScrollInput(
    enabled: Boolean,
    listState: LazyListState,
): Modifier

@Composable
internal expect fun DesktopConversationScrollbar(
    enabled: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
)
