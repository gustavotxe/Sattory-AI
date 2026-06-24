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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.sattory.app.model.ModelStatus
import org.sattory.app.ui.theme.SattoryTheme

@Composable
fun ChatScreen(viewModel: ChatViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SattoryTheme(darkTheme = state.themeMode == ThemeMode.Dark) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                selected = state.selectedDestination,
                onMenuClick = onMenuClick,
                onNewChat = { onIntent(ChatIntent.NewConversation) },
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
    onSelect: (ChatDestination) -> Unit,
    onNewChat: () -> Unit,
    onRetryModel: () -> Unit,
    onToggleExpanded: () -> Unit = {},
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NavigationHeader(showDesktopToggle, onToggleExpanded)
        FilledTonalButton(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Outlined.AddComment, null)
            Spacer(Modifier.width(10.dp))
            Text("Nova conversa", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Espaco de trabalho",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        NavigationEntries.forEach { item ->
            val isSelected = selected == item.destination
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
                badge = item.badge?.let { badge ->
                    {
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(badge, style = MaterialTheme.typography.labelSmall)
                        }
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
        Spacer(Modifier.weight(1f))
        NavigationQuickStats()
        ModelStatusCard(modelStatus, onRetryModel)
    }
}

private data class NavigationEntry(
    val destination: ChatDestination,
    val supportingText: String,
    val badge: String? = null,
)

private val NavigationEntries = listOf(
    NavigationEntry(ChatDestination.Chat, "Conversa atual"),
    NavigationEntry(ChatDestination.SavedConversations, "Colecao de chats", "0"),
    NavigationEntry(ChatDestination.FileProfiles, "Fontes para RAG", "0"),
    NavigationEntry(ChatDestination.History, "Linha do tempo"),
    NavigationEntry(ChatDestination.Settings, "Preferencias do app"),
)

@Composable
private fun NavigationHeader(showDesktopToggle: Boolean, onToggleExpanded: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Sattory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "IA local para estudos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showDesktopToggle) {
                IconButton(onClick = onToggleExpanded) {
                    Icon(Icons.Outlined.KeyboardDoubleArrowLeft, "Recolher menu")
                }
            }
        }
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
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            )
            Surface(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).clickable { onSelect(item.destination) },
                color = containerColor,
                shape = RoundedCornerShape(16.dp),
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

@Composable
private fun NavigationQuickStats() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactMetric("0", "salvas", Modifier.weight(1f))
        CompactMetric("0", "arquivos", Modifier.weight(1f))
    }
}

@Composable
private fun CompactMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ChatDestination.icon() = when (this) {
    ChatDestination.Chat -> Icons.Outlined.ChatBubbleOutline
    ChatDestination.SavedConversations -> Icons.Outlined.Bookmarks
    ChatDestination.FileProfiles -> Icons.Outlined.FolderOpen
    ChatDestination.History -> Icons.Outlined.History
    ChatDestination.Settings -> Icons.Outlined.Settings
}

private fun ChatDestination.topBarTitle() = when (this) {
    ChatDestination.Chat -> "Nova conversa"
    ChatDestination.SavedConversations -> "Conversas salvas"
    ChatDestination.FileProfiles -> "Arquivos adicionados"
    ChatDestination.History -> "Historico"
    ChatDestination.Settings -> "Ajustes"
}

private fun ChatDestination.topBarSubtitle() = when (this) {
    ChatDestination.Chat -> "Nenhuma fonte selecionada"
    ChatDestination.SavedConversations -> "Seus chats favoritos aparecerao aqui"
    ChatDestination.FileProfiles -> "Perfis de texto para o futuro RAG"
    ChatDestination.History -> "Conversas recentes e retomadas"
    ChatDestination.Settings -> "Preferencias visuais e de modelo"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    compact: Boolean,
    selected: ChatDestination,
    onMenuClick: () -> Unit,
    onNewChat: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(selected.topBarTitle(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    selected.topBarSubtitle(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            if (compact) IconButton(onClick = onMenuClick) { Icon(Icons.Outlined.Menu, "Abrir menu") }
        },
        actions = {
            IconButton(onClick = onNewChat) { Icon(Icons.Outlined.AddComment, "Nova conversa") }
            IconButton(onClick = {}) { Icon(Icons.Outlined.Tune, "Ajustes rapidos") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
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
            if (state.selectedDestination != ChatDestination.Chat) {
                DestinationOverview(
                    state = state,
                    onIntent = onIntent,
                    expanded = expanded,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else if (state.messages.isEmpty()) {
                WelcomeContent(Modifier.align(Alignment.Center), expanded)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = if (expanded) 48.dp else 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            expanded = expanded,
                            actionsVisible = state.selectedMessageId == message.id,
                            onToggleActions = if (message.isFromUser && !state.isGenerating) {
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
private fun SectionHero(destination: ChatDestination, expanded: Boolean) {
    val container by animateColorAsState(
        if (destination == ChatDestination.FileProfiles) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
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
                Text(destination.topBarTitle(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Contrast, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tema do aplicativo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
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
                    Text("Menu desktop expandido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
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
    ChatDestination.SavedConversations -> listOf(
        OverviewItem("Nenhuma conversa fixada", "Quando o recurso for ligado, chats importantes ficarao neste espaco.", "UI pronta", Icons.Outlined.BookmarkBorder),
        OverviewItem("Colecoes futuras", "Organize estudos, projetos e pesquisas em grupos separados.", "Em breve", Icons.Outlined.Inventory2),
    )
    ChatDestination.FileProfiles -> listOf(
        OverviewItem("Perfis de arquivos", "Textos adicionados pelo usuario poderao ser revisados antes de entrar no RAG.", "0 fontes", Icons.Outlined.Description),
        OverviewItem("Saude da base", "Espaco reservado para tamanho, data de importacao e status de indexacao.", "Planejado", Icons.Outlined.DataUsage),
    )
    ChatDestination.History -> listOf(
        OverviewItem("Historico vazio", "As conversas recentes aparecerao aqui quando a persistencia for implementada.", "Local", Icons.Outlined.Schedule),
        OverviewItem("Retomar contexto", "Area preparada para continuar conversas anteriores sem misturar fontes.", "Futuro", Icons.Outlined.Restore),
    )
    ChatDestination.Settings -> listOf(
        OverviewItem("Aparencia", "Tema, densidade e preferencias de leitura poderao morar nesta secao.", "Material 3", Icons.Outlined.Palette),
        OverviewItem("Modelo local", "Parametros do modelo e armazenamento ficarao agrupados com clareza.", "Privado", Icons.Outlined.Memory),
    )
}

@Composable
private fun WelcomeContent(modifier: Modifier, expanded: Boolean) {
    Column(
        modifier = modifier.widthIn(max = 720.dp).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "Como posso ajudar nos seus estudos?",
            style = if (expanded) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Converse com seus documentos com privacidade e inteligência local.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                Text(message.text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            }
            if (actionsVisible) onEdit?.let {
                TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar")
                }
            }
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
                    Text("Editando mensagem", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancelEditing) { Text("Cancelar") }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 2.dp,
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.weight(1f),
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
                    FilledIconButton(onClick = onSend, enabled = value.isNotBlank() && !generating && modelReady) {
                        Icon(Icons.Rounded.ArrowUpward, "Enviar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelStatusCard(status: ModelStatus, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Modelo local", style = MaterialTheme.typography.labelLarge)
                    Text(status.label(), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (status is ModelStatus.Downloading) {
                status.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                    ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (status is ModelStatus.Failed) {
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) { Text("Tentar novamente") }
            }
        }
    }
}

@Composable
private fun ModelStatusBanner(status: ModelStatus, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status !is ModelStatus.Failed) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(status.label(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (status is ModelStatus.Failed) TextButton(onClick = onRetry) { Text("Tentar novamente") }
        }
    }
}

private fun ModelStatus.label(): String = when (this) {
    ModelStatus.Checking -> "Verificando arquivo…"
    ModelStatus.Queued -> "Download aguardando conexão…"
    is ModelStatus.Downloading -> progress?.let { "Baixando ${(it * 100).toInt()}%" } ?: "Baixando modelo…"
    ModelStatus.Verifying -> "Validando segurança do arquivo…"
    ModelStatus.Ready -> "Pronto para uso"
    is ModelStatus.Failed -> message
}

@Composable
private fun BrandMark(size: Int = 38) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * .32f).dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size((size * .55f).dp),
        )
    }
}
