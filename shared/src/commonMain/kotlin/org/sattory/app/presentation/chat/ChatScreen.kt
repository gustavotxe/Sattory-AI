package org.sattory.app.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.sattory.app.model.ModelArtifactSpec
import org.sattory.app.model.ModelStatus
import org.sattory.app.rag.domain.RagIndexState
import org.sattory.app.rag.domain.RagSource
import org.sattory.app.ui.theme.SattoryTheme
import sattoryapp.shared.generated.resources.Res
import sattoryapp.shared.generated.resources.sattory_app_icon

@Composable
fun ChatScreen(viewModel: ChatViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SattoryTheme(darkTheme = state.themeMode == ThemeMode.Dark) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
            if (maxWidth >= 800.dp) {
                Row(Modifier.fillMaxSize()) {
                    DesktopNavigation(
                        selected = state.selectedDestination,
                        modelStatus = state.modelStatus,
                        expanded = state.desktopNavigationExpanded,
                        onSelect = { viewModel.onIntent(ChatIntent.DestinationSelected(it)) },
                        onNewChat = { viewModel.onIntent(ChatIntent.NewConversation) },
                        onRetryModel = { viewModel.onIntent(ChatIntent.RetryModelDownload) },
                        onToggleExpanded = { viewModel.onIntent(ChatIntent.ToggleDesktopNavigation) },
                    )
                    ChatScaffold(
                        state = state,
                        onIntent = viewModel::onIntent,
                        expanded = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                MobileChatLayout(state, viewModel::onIntent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileChatLayout(state: ChatState, onIntent: (ChatIntent) -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MobileDrawerContent(
                selected = state.selectedDestination,
                modelStatus = state.modelStatus,
                onSelect = {
                    onIntent(ChatIntent.DestinationSelected(it))
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    onIntent(ChatIntent.NewConversation)
                    scope.launch { drawerState.close() }
                },
                onRetryModel = { onIntent(ChatIntent.RetryModelDownload) },
            )
        },
    ) {
        ChatScaffold(
            state = state,
            onIntent = onIntent,
            expanded = false,
            onMenuClick = { scope.launch { drawerState.open() } },
        )
    }
}

@Composable
private fun ChatScaffold(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatTopBar(
                compact = !expanded,
                state = state,
                onMenuClick = onMenuClick,
                onRequestSave = { onIntent(ChatIntent.RequestSaveConversation) },
                onIntent = onIntent,
            )
        },
    ) { padding ->
        ChatContent(
            state = state,
            onIntent = onIntent,
            modifier = Modifier.padding(padding),
            expanded = expanded,
        )
    }
    SaveConversationDialog(state = state, onIntent = onIntent)
    OpenSavedConversationDialog(state = state, onIntent = onIntent)
    NewConversationDialog(state = state, onIntent = onIntent)
}

@Composable
private fun MobileDrawerContent(
    selected: ChatDestination,
    modelStatus: ModelStatus,
    onSelect: (ChatDestination) -> Unit,
    onNewChat: () -> Unit,
    onRetryModel: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        NavigationContent(
            selected = selected,
            modelStatus = modelStatus,
            onSelect = onSelect,
            onNewChat = onNewChat,
            onRetryModel = onRetryModel,
            modifier = Modifier.fillMaxSize().padding(16.dp),
        )
    }
}

@Composable
private fun DesktopNavigation(
    selected: ChatDestination,
    modelStatus: ModelStatus,
    expanded: Boolean,
    onSelect: (ChatDestination) -> Unit,
    onNewChat: () -> Unit,
    onRetryModel: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val width by animateDpAsState(if (expanded) 312.dp else 76.dp)
    Surface(
        modifier = Modifier.width(width).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        if (expanded) {
            NavigationContent(
                selected = selected,
                modelStatus = modelStatus,
                showDesktopToggle = true,
                desktopStyle = true,
                onSelect = onSelect,
                onNewChat = onNewChat,
                onRetryModel = onRetryModel,
                onToggleExpanded = onToggleExpanded,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        } else {
            CompactDesktopNavigation(
                selected = selected,
                onSelect = onSelect,
                onNewChat = onNewChat,
                onToggleExpanded = onToggleExpanded,
                modifier = Modifier.fillMaxSize().padding(vertical = 14.dp, horizontal = 10.dp),
            )
        }
    }
}

@Composable
private fun NavigationContent(
    selected: ChatDestination,
    modelStatus: ModelStatus,
    showDesktopToggle: Boolean = false,
    desktopStyle: Boolean = false,
    onSelect: (ChatDestination) -> Unit,
    onNewChat: () -> Unit,
    onRetryModel: () -> Unit,
    onToggleExpanded: () -> Unit = {},
    modifier: Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(if (desktopStyle) 10.dp else 8.dp)
    ) {
        NavigationHeader(showDesktopToggle, onToggleExpanded, desktopStyle)
        WorkspaceSectionLabel(desktopStyle)
        if (desktopStyle) {
            DesktopNavigationAction(onNewChat)
        } else {
            NavigationDrawerItem(
                icon = { Icon(Icons.Outlined.AddComment, null) },
                label = { Text("Nova conversa") },
                selected = false,
                onClick = onNewChat,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )
        }
        NavigationEntries.forEach { item ->
            val isSelected = selected == item.destination
            if (desktopStyle) {
                DesktopNavigationItem(
                    item = item,
                    selected = isSelected,
                    onClick = { onSelect(item.destination) },
                )
            } else {
                val itemHeight by animateDpAsState(if (isSelected) 64.dp else 58.dp)
                NavigationDrawerItem(
                    icon = { Icon(item.destination.icon(), null) },
                    label = {
                        Column {
                            Text(item.destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                item.supportingText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    selected = isSelected,
                    onClick = { onSelect(item.destination) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(itemHeight),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        ModelStatusCard(
            status = modelStatus,
            onRetry = onRetryModel,
        )
    }
}

@Composable
private fun WorkspaceSectionLabel(desktopStyle: Boolean) {
    Text(
        "Espaço de trabalho:",
        style = if (desktopStyle) {
            MaterialTheme.typography.labelSmall
        } else {
            MaterialTheme.typography.labelMedium
        },
        fontWeight = if (desktopStyle) FontWeight.Bold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = if (desktopStyle) 2.dp else 4.dp),
    )
}

@Composable
private fun DesktopNavigationAction(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    Icons.Outlined.AddComment,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(8.dp).size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Nova conversa",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DesktopNavigationItem(
    item: NavigationEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    )
    Surface(
        modifier = Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = containerColor,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    item.destination.icon(),
                    null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(8.dp).size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.destination.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Surface(
                    modifier = Modifier.size(width = 4.dp, height = 28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                ) {}
            }
        }
    }
}

private data class NavigationEntry(
    val destination: ChatDestination,
    val supportingText: String,
)

private val NavigationEntries = listOf(
    NavigationEntry(ChatDestination.Chat, "Conversa atual"),
    NavigationEntry(ChatDestination.SavedConversations, "Coleção de chats"),
    NavigationEntry(ChatDestination.FileProfiles, "Fontes para consulta"),
    NavigationEntry(ChatDestination.Settings, "Preferencias do app"),
)

@Composable
private fun NavigationHeader(
    showDesktopToggle: Boolean,
    onToggleExpanded: () -> Unit,
    desktopStyle: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        BrandMark(size = if (desktopStyle) 46 else 38)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Sattory",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "IA Local",
                style = if (desktopStyle) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (desktopStyle) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDesktopToggle) {
            if (desktopStyle) {
                IconButton(onClick = onToggleExpanded, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Outlined.KeyboardDoubleArrowLeft, "Recolher menu")
                }
            } else {
                IconButton(onClick = onToggleExpanded) {
                    Icon(Icons.Outlined.KeyboardDoubleArrowLeft, "Recolher menu")
                }
            }
        }
    }
    if (desktopStyle) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun CompactDesktopNavigation(
    selected: ChatDestination,
    onSelect: (ChatDestination) -> Unit,
    onNewChat: () -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BrandMark(size = 42)
        FilledIconButton(onClick = onNewChat, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.AddComment, "Nova conversa")
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        NavigationEntries.forEach { item ->
            val isSelected = selected == item.destination
            val containerColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            Surface(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(18.dp))
                    .clickable { onSelect(item.destination) },
                color = containerColor,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = if (isSelected) 2.dp else 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        item.destination.icon(),
                        item.destination.label,
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleExpanded) {
            Icon(Icons.Outlined.KeyboardDoubleArrowRight, "Expandir menu")
        }
    }
}

private fun ChatDestination.icon() = when (this) {
    ChatDestination.Chat -> Icons.Outlined.ChatBubbleOutline
    ChatDestination.SavedConversations -> Icons.Outlined.Bookmarks
    ChatDestination.FileProfiles -> Icons.Outlined.FolderOpen
    ChatDestination.Settings -> Icons.Outlined.Settings
}

private fun ChatState.topBarTitle(): String = when (selectedDestination) {
    ChatDestination.Chat -> currentConversationTitle ?: "Conversa não salva"
    ChatDestination.SavedConversations -> "Conversas Salvas"
    ChatDestination.FileProfiles -> "Arquivos Adicionados"
    ChatDestination.Settings -> "Ajustes"
}

private fun ChatDestination.sectionTitle() = when (this) {
    ChatDestination.Chat -> "Nova conversa"
    ChatDestination.SavedConversations -> "Conversas Salvas"
    ChatDestination.FileProfiles -> "Arquivos Adicionados"
    ChatDestination.Settings -> "Ajustes"
}

private fun ChatDestination.topBarSubtitle() = when (this) {
    ChatDestination.SavedConversations -> "Seus chats favoritos aparecerao aqui"
    ChatDestination.FileProfiles -> "Perfis de texto para consultas"
    ChatDestination.Settings -> "Preferências visuais do sistema"
    ChatDestination.Chat -> "Nenhuma fonte selecionada"
}

private fun ChatState.topBarStatus(): String? = when {
    selectedDestination != ChatDestination.Chat -> selectedDestination.topBarSubtitle()
    currentConversationTitle != null && !currentConversationSaved -> "Conversa não salva"
    else -> null
}

private fun ChatState.topBarSource(): String? = when (selectedDestination) {
    ChatDestination.Chat -> selectedCollection?.name ?: "Nenhuma fonte selecionada"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
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

@Composable
private fun SaveConversationDialog(state: ChatState, onIntent: (ChatIntent) -> Unit) {
    if (!state.showSaveConversationDialog) return

    AlertDialog(
        onDismissRequest = { onIntent(ChatIntent.DismissSaveConversation) },
        icon = { Icon(Icons.Outlined.BookmarkAdd, null) },
        title = { Text("Salvar conversa?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A conversa atual sera salva localmente neste dispositivo.")
                state.selectedCollection?.let {
                    AssistChip(
                        onClick = {},
                        label = { Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
                state.saveConversationMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isSavingConversation) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onIntent(ChatIntent.ConfirmSaveConversation) },
                enabled = !state.isSavingConversation,
            ) {
                if (state.isSavingConversation) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onIntent(ChatIntent.DismissSaveConversation) },
                enabled = !state.isSavingConversation,
            ) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun OpenSavedConversationDialog(state: ChatState, onIntent: (ChatIntent) -> Unit) {
    if (!state.showOpenSavedConversationDialog) return

    AlertDialog(
        onDismissRequest = { onIntent(ChatIntent.DismissOpenSavedConversation) },
        icon = { Icon(Icons.Outlined.WarningAmber, null) },
        title = { Text("Abrir conversa salva?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A conversa atual ainda nao foi salva e sera substituida pela conversa escolhida.")
                Text(
                    "Voce pode salvar a conversa atual antes de abrir a conversa salva.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.saveConversationMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onIntent(ChatIntent.SaveAndOpenSavedConversation) },
                enabled = !state.isSavingConversation,
            ) {
                if (state.isSavingConversation) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Salvar e abrir")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onIntent(ChatIntent.ConfirmOpenSavedConversation) },
                    enabled = !state.isSavingConversation,
                ) {
                    Text("Abrir sem salvar")
                }
                TextButton(
                    onClick = { onIntent(ChatIntent.DismissOpenSavedConversation) },
                    enabled = !state.isSavingConversation,
                ) {
                    Text("Cancelar")
                }
            }
        },
    )
}

@Composable
private fun NewConversationDialog(state: ChatState, onIntent: (ChatIntent) -> Unit) {
    if (!state.showNewConversationDialog) return

    AlertDialog(
        onDismissRequest = { onIntent(ChatIntent.DismissNewConversation) },
        icon = { Icon(Icons.Outlined.AddComment, null) },
        title = { Text("Iniciar nova conversa?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A conversa atual ainda nao foi salva e sera removida da tela.")
                Text(
                    "Voce pode salva-la localmente antes de iniciar uma nova conversa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.saveConversationMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onIntent(ChatIntent.SaveAndNewConversation) },
                enabled = !state.isSavingConversation,
            ) {
                if (state.isSavingConversation) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Salvar e iniciar")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onIntent(ChatIntent.ConfirmNewConversation) },
                    enabled = !state.isSavingConversation,
                ) {
                    Text("Iniciar sem salvar")
                }
                TextButton(
                    onClick = { onIntent(ChatIntent.DismissNewConversation) },
                    enabled = !state.isSavingConversation,
                ) {
                    Text("Cancelar")
                }
            }
        },
    )
}

@Composable
private fun ChatContent(
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
private fun DestinationOverview(
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
private fun EmptySavedConversationsCard(modifier: Modifier = Modifier) {
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
private fun SavedConversationCard(
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

@Composable
private fun FileProfilesContent(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = if (expanded) 48.dp else 16.dp,
            vertical = if (expanded) 32.dp else 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { SectionHero(ChatDestination.FileProfiles, expanded) }
        item {
            RagStatusPanel(
                embeddingStatus = state.embeddingModelStatus,
                indexState = state.ragIndexState,
                onRetry = { onIntent(ChatIntent.RetryEmbeddingModelDownload) },
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            )
        }
        item {
            CollectionSelectorPanel(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            )
        }
        item {
            CreateCollectionPanel(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RagStatusPanel(
    embeddingStatus: ModelStatus,
    indexState: RagIndexState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Hub, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "RAG local",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Embeddings multilingual-e5-small para PT e EN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("Modelo: ${embeddingStatus.label()}", style = MaterialTheme.typography.bodySmall)
            if (embeddingStatus is ModelStatus.Downloading) {
                embeddingStatus.progress?.let {
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                    ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when (indexState) {
                RagIndexState.Indexing -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                is RagIndexState.Failed -> Text(
                    indexState.message,
                    color = MaterialTheme.colorScheme.error
                )

                else -> Unit
            }
            if (embeddingStatus is ModelStatus.Failed) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Tentar novamente") }
            }
        }
    }
}

@Composable
private fun CollectionSelectorPanel(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FolderCopy, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Colecao ativa",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        state.selectedCollection?.name ?: "Nenhuma fonte selecionada",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = { onIntent(ChatIntent.CollectionSelected(null)) },
                    label = { Text("Limpar") })
            }
            if (state.collections.isEmpty()) {
                Text(
                    "Crie uma colecao com texto colado para usar RAG na conversa.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.collections.forEach { collection ->
                    val selected = state.selectedCollectionId == collection.id
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .clickable { onIntent(ChatIntent.CollectionSelected(collection.id)) },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.Description,
                                null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    collection.name,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${collection.documentCount} texto(s), ${collection.chunkCount} chunk(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onIntent(ChatIntent.EditCollection(collection.id)) }) {
                                Icon(Icons.Outlined.Edit, "Editar colecao")
                            }
                            IconButton(onClick = { onIntent(ChatIntent.DeleteCollection(collection.id)) }) {
                                Icon(Icons.Outlined.Delete, "Excluir colecao")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateCollectionPanel(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editing = state.editingCollectionId != null
    val canSubmit = state.collectionTitleInput.isNotBlank() &&
            state.collectionTextInput.trim().length >= 40 &&
            state.ragIndexState != RagIndexState.Indexing &&
            state.embeddingModelStatus is ModelStatus.Ready

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
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (editing) "Editar colecao" else "Nova colecao",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (editing) {
                    TextButton(onClick = { onIntent(ChatIntent.CancelCollectionEditing) }) {
                        Text("Cancelar")
                    }
                }
            }
            OutlinedTextField(
                value = state.collectionTitleInput,
                onValueChange = { onIntent(ChatIntent.CollectionTitleChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Titulo") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.collectionTextInput,
                onValueChange = { onIntent(ChatIntent.CollectionTextChanged(it)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                label = { Text("Texto colado") },
                minLines = 8,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.ragIndexState == RagIndexState.Indexing) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Button(
                    onClick = { onIntent(if (editing) ChatIntent.SaveCollection else ChatIntent.CreateCollection) },
                    enabled = canSubmit,
                ) {
                    Icon(
                        if (editing) Icons.Outlined.Save else Icons.Outlined.Add,
                        null,
                        Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (editing) "Salvar colecao" else "Criar colecao")
                }
            }
        }
    }
}

@Composable
private fun SectionHero(destination: ChatDestination, expanded: Boolean) {
    val container by animateColorAsState(
        MaterialTheme.colorScheme.surfaceContainerHigh
    )
    Surface(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        color = container,
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            Modifier.padding(if (expanded) 24.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(
                    destination.icon(),
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(14.dp).size(26.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    destination.sectionTitle(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    destination.topBarSubtitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(item: OverviewItem, modifier: Modifier = Modifier) {
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
            Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(onClick = {}, label = { Text(item.tag) })
        }
    }
}

@Composable
private fun SettingsPanel(
    themeMode: ThemeMode,
    desktopNavigationExpanded: Boolean,
    onThemeSelected: (ThemeMode) -> Unit,
    onToggleDesktopNavigation: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Contrast, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Tema do aplicativo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "O app inicia em modo claro por padrao.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeSelected(mode) },
                        label = { Text(mode.label) },
                        leadingIcon = if (themeMode == mode) {
                            { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WidthNormal, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Menu desktop expandido",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (desktopNavigationExpanded) "A sidebar mostra textos, status e atalhos." else "A sidebar compacta libera mais espaco para o chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = desktopNavigationExpanded,
                    onCheckedChange = { onToggleDesktopNavigation() },
                )
            }
        }
    }
}

private data class OverviewItem(
    val title: String,
    val description: String,
    val tag: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private fun ChatDestination.overviewItems(): List<OverviewItem> = when (this) {
    ChatDestination.Chat -> emptyList()
    ChatDestination.SavedConversations -> emptyList()
    ChatDestination.FileProfiles -> listOf(
        OverviewItem(
            "Perfis de arquivos",
            "Textos adicionados pelo usuario poderao ser revisados antes de entrar no RAG.",
            "0 fontes",
            Icons.Outlined.Description
        ),
        OverviewItem(
            "Saude da base",
            "Espaco reservado para tamanho, data de importacao e status de indexacao.",
            "Planejado",
            Icons.Outlined.DataUsage
        ),
    )

    ChatDestination.Settings -> emptyList()
}

@Composable
private fun WelcomeContent(modifier: Modifier, expanded: Boolean, selectedCollectionName: String?) {
    Column(
        modifier = modifier.widthIn(max = 720.dp).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "Como posso ajudar?",
            style = if (expanded) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            selectedCollectionName?.let { "Fonte ativa: $it" }
                ?: "Converse com seus documentos com privacidade e inteligencia local.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SourceStrip(sources: List<RagSource>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ManageSearch,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Fontes usadas", style = MaterialTheme.typography.labelLarge)
            }
            sources.take(3).forEach { source ->
                Text(
                    source.documentTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    expanded: Boolean,
    actionsVisible: Boolean,
    onToggleActions: (() -> Unit)?,
    onEdit: (() -> Unit)?,
) {
    val clipboardManager = LocalClipboardManager.current
    val bubbleShape = if (message.isFromUser) {
        RoundedCornerShape(20.dp, 5.dp, 20.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp)
    }
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!message.isFromUser) {
            BrandMark(size = 34)
            Spacer(Modifier.width(10.dp))
        }
        Column(horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start) {
            Surface(
                modifier = Modifier
                    .widthIn(max = if (expanded) 620.dp else 300.dp)
                    .clip(bubbleShape)
                    .then(if (onToggleActions != null) Modifier.clickable(onClick = onToggleActions) else Modifier),
                color = if (message.isFromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = bubbleShape,
                tonalElevation = if (message.isFromUser) 0.dp else 1.dp,
            ) {
                Text(
                    message.text,
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (actionsVisible) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { clipboardManager.setText(AnnotatedString(message.text)) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copiar")
                    }
                    onEdit?.let {
                        TextButton(
                            onClick = it,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Editar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueResponseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
    ) {
        FilledTonalButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Continue")
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark(size = 34)
        Spacer(Modifier.width(10.dp))
        LinearProgressIndicator(Modifier.width(64.dp).clip(CircleShape))
    }
}

@Composable
private fun Composer(
    value: String,
    generating: Boolean,
    modelReady: Boolean,
    editing: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelEditing: () -> Unit,
    expanded: Boolean,
    submitOnEnter: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)
                .padding(horizontal = if (expanded) 32.dp else 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (editing) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Editando mensagem",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancelEditing) { Text("Cancelar") }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                ),
                shadowElevation = 2.dp,
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                            if (
                                submitOnEnter &&
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.Enter &&
                                !event.isShiftPressed
                            ) {
                                onSend()
                                true
                            } else {
                                false
                            }
                        },
                        enabled = modelReady && !generating,
                        placeholder = {
                            Text(
                                if (modelReady) "Pergunte sobre seus documentos…" else "Preparando modelo local…",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        minLines = 1,
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                    FilledIconButton(
                        onClick = onSend,
                        enabled = value.isNotBlank() && !generating && modelReady
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, "Enviar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelStatusCard(
    status: ModelStatus,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Memory, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Modelo local", style = MaterialTheme.typography.labelLarge)
                    Text(
                        ModelArtifactSpec.SHORT_NAME,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "${ModelArtifactSpec.APPROXIMATE_SIZE} - ${status.compactLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (status is ModelStatus.Downloading) {
                status.progress?.let {
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                    ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (status is ModelStatus.Failed) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Tentar novamente") }
            }
        }
    }
}

@Composable
private fun ModelStatusBanner(status: ModelStatus, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status !is ModelStatus.Failed) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                status.label(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (status is ModelStatus.Failed) TextButton(onClick = onRetry) { Text("Tentar novamente") }
        }
    }
}

private fun ModelStatus.label(): String = when (this) {
    ModelStatus.Checking -> "Verificando arquivo…"
    ModelStatus.Queued -> "Download aguardando conexão…"
    is ModelStatus.Downloading -> progress?.let { "Baixando ${(it * 100).toInt()}%" }
        ?: "Baixando modelo…"

    ModelStatus.Verifying -> "Validando segurança do arquivo…"
    ModelStatus.Ready -> "Pronto para uso"
    is ModelStatus.Failed -> message
}

private fun ModelStatus.compactLabel(): String = when (this) {
    ModelStatus.Checking -> "Verificando"
    ModelStatus.Queued -> "Aguardando"
    is ModelStatus.Downloading -> progress?.let { "Baixando ${(it * 100).toInt()}%" } ?: "Baixando"
    ModelStatus.Verifying -> "Validando"
    ModelStatus.Ready -> "Pronto para uso"
    is ModelStatus.Failed -> "Falhou"
}

@Composable
private fun BrandMark(size: Int = 38) {
    Image(
        painter = painterResource(Res.drawable.sattory_app_icon),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size * .28f).dp)),
    )
}
