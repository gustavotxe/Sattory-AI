package org.sattory.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow

internal fun ChatState.topBarTitle(): String = when (selectedDestination) {
    ChatDestination.Chat -> currentConversationTitle ?: "Conversa não salva"
    ChatDestination.SavedConversations -> "Conversas Salvas"
    ChatDestination.FileProfiles -> "Arquivos Adicionados"
    ChatDestination.Settings -> "Ajustes"
}

internal fun ChatDestination.sectionTitle() = when (this) {
    ChatDestination.Chat -> "Nova conversa"
    ChatDestination.SavedConversations -> "Conversas Salvas"
    ChatDestination.FileProfiles -> "Arquivos Adicionados"
    ChatDestination.Settings -> "Ajustes"
}

internal fun ChatDestination.topBarSubtitle() = when (this) {
    ChatDestination.SavedConversations -> "Seus chats favoritos aparecerao aqui"
    ChatDestination.FileProfiles -> "Perfis de texto para consultas"
    ChatDestination.Settings -> "Preferências visuais do sistema"
    ChatDestination.Chat -> "Nenhuma fonte selecionada"
}

internal fun ChatState.topBarStatus(): String? = when {
    selectedDestination != ChatDestination.Chat -> selectedDestination.topBarSubtitle()
    currentConversationTitle != null && !currentConversationSaved -> "Conversa não salva"
    else -> null
}

internal fun ChatState.topBarSource(): String? = when (selectedDestination) {
    ChatDestination.Chat -> selectedCollection?.name ?: "Nenhuma fonte selecionada"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(
    compact: Boolean,
    state: ChatState,
    onMenuClick: () -> Unit,
    onRequestSave: () -> Unit,
    onIntent: (ChatIntent) -> Unit,
) {
    var collectionMenuExpanded by remember { mutableStateOf(false) }
    val selected = state.selectedDestination
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.topBarTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.topBarStatus()?.let { status ->
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (
                            state.selectedDestination == ChatDestination.Chat &&
                            (state.currentSavedConversationId == null || !state.currentConversationSaved)
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                state.topBarSource()?.let { source ->
                    Text(
                        source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (compact) IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Outlined.Menu,
                    "Abrir menu"
                )
            }
        },
        actions = {
            IconButton(
                onClick = onRequestSave,
                enabled = state.selectedDestination == ChatDestination.Chat &&
                        state.messages.isNotEmpty() &&
                        !state.isGenerating &&
                        !state.isSavingConversation,
            ) {
                Icon(Icons.Outlined.BookmarkAdd, "Salvar conversa")
            }
            Box {
                IconButton(
                    onClick = { collectionMenuExpanded = true },
                    enabled = selected == ChatDestination.Chat,
                ) {
                    Icon(
                        if (state.selectedCollectionId == null) Icons.Outlined.Source else Icons.Outlined.FolderOpen,
                        "Selecionar coleção",
                    )
                }
                DropdownMenu(
                    expanded = collectionMenuExpanded,
                    onDismissRequest = { collectionMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Nenhuma fonte")
                                Text(
                                    "Conversar sem RAG",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                if (state.selectedCollectionId == null) Icons.Outlined.Check else Icons.Outlined.Block,
                                null,
                            )
                        },
                        onClick = {
                            onIntent(ChatIntent.CollectionSelected(null))
                            collectionMenuExpanded = false
                        },
                    )
                    if (state.collections.isNotEmpty()) {
                        HorizontalDivider()
                    }
                    state.collections.forEach { collection ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        collection.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${collection.chunkCount} chunk(s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    if (state.selectedCollectionId == collection.id) Icons.Outlined.CheckCircle else Icons.Outlined.Description,
                                    null,
                                )
                            },
                            onClick = {
                                onIntent(ChatIntent.CollectionSelected(collection.id))
                                collectionMenuExpanded = false
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Gerenciar arquivos") },
                        leadingIcon = { Icon(Icons.Outlined.FolderCopy, null) },
                        onClick = {
                            onIntent(ChatIntent.DestinationSelected(ChatDestination.FileProfiles))
                            collectionMenuExpanded = false
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

