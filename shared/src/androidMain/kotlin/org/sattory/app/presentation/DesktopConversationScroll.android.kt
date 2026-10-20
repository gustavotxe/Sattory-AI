package org.sattory.app.presentation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun Modifier.desktopConversationScrollInput(
    enabled: Boolean,
    listState: LazyListState,
): Modifier = this

@Composable
internal actual fun DesktopConversationScrollbar(
    enabled: Boolean,
    listState: LazyListState,
    modifier: Modifier,
) = Unit
