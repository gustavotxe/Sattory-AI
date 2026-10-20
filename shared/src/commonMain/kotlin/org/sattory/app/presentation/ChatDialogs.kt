package org.sattory.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SaveConversationDialog(state: ChatState, onIntent: (ChatIntent) -> Unit) {
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
internal fun OpenSavedConversationDialog(state: ChatState, onIntent: (ChatIntent) -> Unit) {
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
internal fun NewConversationDialog(state: ChatState, onIntent: (ChatIntent) -> Unit) {
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
