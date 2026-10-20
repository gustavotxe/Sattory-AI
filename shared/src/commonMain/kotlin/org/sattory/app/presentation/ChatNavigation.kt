package org.sattory.app.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import org.sattory.app.model.ModelStatus

@Composable
internal fun MobileChatLayout(state: ChatState, onIntent: (ChatIntent) -> Unit) {
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
internal fun ChatScaffold(
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
internal fun MobileDrawerContent(
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
internal fun DesktopNavigation(
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
internal fun NavigationContent(
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
internal fun WorkspaceSectionLabel(desktopStyle: Boolean) {
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
internal fun DesktopNavigationAction(onClick: () -> Unit) {
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
internal fun DesktopNavigationItem(
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

internal data class NavigationEntry(
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
internal fun NavigationHeader(
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
internal fun CompactDesktopNavigation(
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

internal fun ChatDestination.icon() = when (this) {
    ChatDestination.Chat -> Icons.Outlined.ChatBubbleOutline
    ChatDestination.SavedConversations -> Icons.Outlined.Bookmarks
    ChatDestination.FileProfiles -> Icons.Outlined.FolderOpen
    ChatDestination.Settings -> Icons.Outlined.Settings
}

