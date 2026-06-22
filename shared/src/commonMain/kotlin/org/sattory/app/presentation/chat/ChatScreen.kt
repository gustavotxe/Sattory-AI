package org.sattory.app.presentation.chat

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatScreen(viewModel: ChatViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (maxWidth >= 800.dp) {
            Row(Modifier.fillMaxSize()) {
                DesktopNavigation(
                    selected = state.selectedDestination,
                    onSelect = { viewModel.onIntent(ChatIntent.DestinationSelected(it)) },
                    onNewChat = { viewModel.onIntent(ChatIntent.NewConversation) },
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
                onSelect = {
                    onIntent(ChatIntent.DestinationSelected(it))
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    onIntent(ChatIntent.NewConversation)
                    scope.launch { drawerState.close() }
                },
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
    onSelect: (ChatDestination) -> Unit,
    onNewChat: () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        NavigationContent(selected, onSelect, onNewChat, Modifier.fillMaxSize().padding(16.dp))
    }
}

@Composable
private fun DesktopNavigation(
    selected: ChatDestination,
    onSelect: (ChatDestination) -> Unit,
    onNewChat: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        NavigationContent(selected, onSelect, onNewChat, Modifier.width(248.dp).fillMaxHeight().padding(16.dp))
    }
}

@Composable
private fun NavigationContent(
    selected: ChatDestination,
    onSelect: (ChatDestination) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp, 8.dp, 8.dp, 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark()
            Spacer(Modifier.width(12.dp))
            Text("Sattory", fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        }
        FilledTonalButton(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Rounded.Edit, null)
            Spacer(Modifier.width(10.dp))
            Text("Nova conversa")
        }
        Spacer(Modifier.height(12.dp))
        ChatDestination.entries.forEach { destination ->
            NavigationDrawerItem(
                icon = { Icon(destination.icon(), null) },
                label = { Text(destination.label) },
                selected = selected == destination,
                onClick = { onSelect(destination) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.weight(1f))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Modelo local", style = MaterialTheme.typography.labelLarge)
                    Text("Ainda não configurado", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun ChatDestination.icon() = when (this) {
    ChatDestination.Chat -> Icons.Outlined.ChatBubbleOutline
    ChatDestination.Library -> Icons.Outlined.FolderOpen
    ChatDestination.History -> Icons.Outlined.History
    ChatDestination.Settings -> Icons.Outlined.Settings
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(compact: Boolean, onMenuClick: () -> Unit, onNewChat: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nova conversa", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Nenhuma fonte selecionada",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        navigationIcon = {
            if (compact) IconButton(onClick = onMenuClick) { Icon(Icons.Outlined.Menu, "Abrir menu") }
        },
        actions = {
            IconButton(onClick = onNewChat) { Icon(Icons.Outlined.AddComment, "Nova conversa") }
            IconButton(onClick = {}) { Icon(Icons.Outlined.MoreVert, "Mais opções") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                WelcomeContent(Modifier.align(Alignment.Center), expanded)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = if (expanded) 48.dp else 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(state.messages, key = { it.id }) { MessageBubble(it, expanded) }
                    if (state.isGenerating) item { ThinkingIndicator() }
                }
            }
        }
        Composer(
            value = state.input,
            generating = state.isGenerating,
            onValueChange = { onIntent(ChatIntent.InputChanged(it)) },
            onSend = { onIntent(ChatIntent.SendMessage) },
            expanded = expanded,
        )
    }
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
private fun MessageBubble(message: ChatMessage, expanded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!message.isFromUser) {
            BrandMark(size = 34)
            Spacer(Modifier.width(10.dp))
        }
        Surface(
            modifier = Modifier.widthIn(max = if (expanded) 620.dp else 300.dp),
            color = if (message.isFromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            shape = if (message.isFromUser) RoundedCornerShape(20.dp, 5.dp, 20.dp, 20.dp) else RoundedCornerShape(20.dp),
            tonalElevation = if (message.isFromUser) 0.dp else 1.dp,
        ) {
            Text(message.text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
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
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    expanded: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)
                .padding(horizontal = if (expanded) 32.dp else 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 2.dp,
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = {}) { Icon(Icons.Outlined.AttachFile, "Adicionar documento") }
                    TextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text("Pergunte sobre seus documentos…", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    FilledIconButton(onClick = onSend, enabled = value.isNotBlank() && !generating) {
                        Icon(Icons.Rounded.ArrowUpward, "Enviar")
                    }
                }
            }
        }
    }
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
