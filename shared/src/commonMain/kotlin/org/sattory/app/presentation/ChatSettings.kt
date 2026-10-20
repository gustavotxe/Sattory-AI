package org.sattory.app.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SectionHero(destination: ChatDestination, expanded: Boolean) {
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
internal fun OverviewCard(item: OverviewItem, modifier: Modifier = Modifier) {
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
internal fun SettingsPanel(
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

internal data class OverviewItem(
    val title: String,
    val description: String,
    val tag: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

internal fun ChatDestination.overviewItems(): List<OverviewItem> = when (this) {
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


