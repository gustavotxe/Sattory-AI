package org.sattory.app.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.sattory.app.model.ModelStatus

@Composable
internal fun ChatContent(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    modifier: Modifier,
    expanded: Boolean,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.modelStatus !is ModelStatus.Ready) {
            ModelStatusBanner(state.modelStatus) { onIntent(ChatIntent.RetryModelDownload) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.selectedDestination == ChatDestination.FileProfiles) {
                FileProfilesContent(
                    state = state,
                    onIntent = onIntent,
                    expanded = expanded,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else if (state.selectedDestination != ChatDestination.Chat) {
                DestinationOverview(
                    state = state,
                    onIntent = onIntent,
                    expanded = expanded,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else if (state.messages.isEmpty()) {
                WelcomeContent(
                    modifier = Modifier.align(Alignment.Center),
                    expanded = expanded,
                    selectedCollectionName = state.selectedCollection?.name,
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .desktopConversationScrollInput(enabled = expanded, listState = listState),
                        contentPadding = PaddingValues(
                            horizontal = if (expanded) 48.dp else 16.dp,
                            vertical = 20.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                expanded = expanded,
                                actionsVisible = state.selectedMessageId == message.id,
                                onToggleActions = if (!state.isGenerating) {
                                    { onIntent(ChatIntent.ToggleMessageActions(message.id)) }
                                } else {
                                    null
                                },
                                onEdit = if (message.isFromUser && !state.isGenerating) {
                                    { onIntent(ChatIntent.EditMessage(message.id)) }
                                } else {
                                    null
                                },
                            )
                        }
                        if (state.isAwaitingFirstToken) item { ThinkingIndicator() }
                        if (state.canContinueLastResponse) {
                            item {
                                ContinueResponseButton(
                                    onClick = { onIntent(ChatIntent.ContinueLastResponse) },
                                    modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
                                )
                            }
                        }
                        if (state.activeSources.isNotEmpty()) {
                            item {
                                SourceStrip(
                                    state.activeSourceDocumentTitles,
                                    state.activeSources,
                                    Modifier.fillMaxWidth().widthIn(max = 840.dp)
                                )
                            }
                        }
                    }
                    DesktopConversationScrollbar(
                        enabled = expanded,
                        listState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = state.selectedDestination == ChatDestination.Chat,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            Composer(
                value = state.input,
                generating = state.isGenerating,
                modelReady = state.modelStatus is ModelStatus.Ready,
                editing = state.editingMessageId != null,
                onValueChange = { onIntent(ChatIntent.InputChanged(it)) },
                onSend = { onIntent(ChatIntent.SendMessage) },
                onCancelEditing = { onIntent(ChatIntent.CancelEditing) },
                expanded = expanded,
                submitOnEnter = expanded,
            )
        }
    }
}

@Composable
internal fun DestinationOverview(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val destination = state.selectedDestination
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = if (expanded) 48.dp else 16.dp,
            vertical = if (expanded) 32.dp else 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            SectionHero(destination, expanded)
        }
        if (destination == ChatDestination.SavedConversations) {
            if (state.savedConversations.isEmpty()) {
                item {
                    EmptySavedConversationsCard(Modifier.widthIn(max = 760.dp).fillMaxWidth())
                }
            } else {
                items(state.savedConversations, key = { it.id }) { conversation ->
                    SavedConversationCard(
                        title = conversation.title,
                        subtitle = conversation.collectionTitle ?: "Sem fonte vinculada",
                        messageCount = conversation.messageCount,
                        onOpen = { onIntent(ChatIntent.OpenSavedConversation(conversation.id)) },
                        onDelete = { onIntent(ChatIntent.DeleteSavedConversation(conversation.id)) },
                        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                    )
                }
            }
        }
        destination.overviewItems().forEach { item ->
            item {
                OverviewCard(item, Modifier.widthIn(max = 760.dp).fillMaxWidth())
            }
        }
        if (destination == ChatDestination.Settings) {
            item {
                SettingsPanel(
                    themeMode = state.themeMode,
                    desktopNavigationExpanded = state.desktopNavigationExpanded,
                    onThemeSelected = { onIntent(ChatIntent.ThemeSelected(it)) },
                    onToggleDesktopNavigation = { onIntent(ChatIntent.ToggleDesktopNavigation) },
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun EmptySavedConversationsCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.BookmarkBorder, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "Nenhuma conversa salva",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Use o icone de salvar na conversa para guardar um chat localmente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SavedConversationCard(
    title: String,
    subtitle: String,
    messageCount: Int,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$subtitle • $messageCount mensagem(ns)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, "Excluir conversa salva")
            }
        }
    }
}
