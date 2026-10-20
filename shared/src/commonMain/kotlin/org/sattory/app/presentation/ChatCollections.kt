package org.sattory.app.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import org.sattory.app.rag.domain.RagIndexState

@Composable
internal fun FileProfilesContent(
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
        item {
            SectionHero(
                ChatDestination.FileProfiles,
                expanded
            )
        }
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
internal fun RagStatusPanel(
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
internal fun CollectionSelectorPanel(
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
internal fun CreateCollectionPanel(
    state: ChatState,
    onIntent: (ChatIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickPdf = rememberPdfPicker { onIntent(ChatIntent.PdfSelected(it)) }
    val busy = state.isImportingPdf || state.ragIndexState == RagIndexState.Indexing
    val editing = state.editingCollectionId != null
    val canSubmit = state.canSubmitCollection

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
                enabled = !busy,
                label = { Text("Titulo") },
                singleLine = true,
            )
            TextButton(onClick = pickPdf, enabled = !busy) {
                Text(if (state.isImportingPdf) "Importando PDF..." else "Selecionar PDF")
            }
            Text("PDF: ate 200 mil caracteres, 20 MB e 500 paginas. Todo o texto sera indexado; apenas trechos relevantes entram na resposta.", style = MaterialTheme.typography.bodySmall)
            state.pdfImportError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(
                value = state.collectionTextInput,
                onValueChange = { onIntent(ChatIntent.CollectionTextChanged(it)) },
                modifier = Modifier.fillMaxWidth().height(220.dp),
                enabled = !busy,
                label = { Text("Texto do documento") },
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
