package org.sattory.app.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
import org.jetbrains.compose.resources.painterResource
import org.sattory.app.model.ModelArtifactSpec
import org.sattory.app.model.ModelStatus
import org.sattory.app.rag.domain.RagSource
import sattoryapp.shared.generated.resources.Res
import sattoryapp.shared.generated.resources.sattory_app_icon

@Composable
internal fun WelcomeContent(modifier: Modifier, expanded: Boolean, selectedCollectionName: String?) {
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
internal fun SourceStrip(documentTitles: List<String>, sources: List<RagSource>, modifier: Modifier = Modifier) {
    var expanded by remember(sources) { mutableStateOf(false) }
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
            documentTitles.take(3).forEach { title ->
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Ocultar trechos" else "Ver trechos consultados")
            }
            if (expanded) {
                sources.forEachIndexed { index, source ->
                    Text(
                        "[Fonte ${index + 1}] ${source.documentTitle}" +
                            (source.chunkIndex?.let { " — trecho ${it + 1}" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(source.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun MessageBubble(
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
internal fun ContinueResponseButton(
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
internal fun ThinkingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark(size = 34)
        Spacer(Modifier.width(10.dp))
        LinearProgressIndicator(Modifier.width(64.dp).clip(CircleShape))
    }
}

@Composable
internal fun Composer(
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
internal fun ModelStatusCard(
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
internal fun ModelStatusBanner(status: ModelStatus, onRetry: () -> Unit) {
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

internal fun ModelStatus.label(): String = when (this) {
    ModelStatus.Checking -> "Verificando arquivo…"
    ModelStatus.Queued -> "Download aguardando conexão…"
    is ModelStatus.Downloading -> progress?.let { "Baixando ${(it * 100).toInt()}%" }
        ?: "Baixando modelo…"

    ModelStatus.Verifying -> "Validando segurança do arquivo…"
    ModelStatus.Ready -> "Pronto para uso"
    is ModelStatus.Failed -> message
}

internal fun ModelStatus.compactLabel(): String = when (this) {
    ModelStatus.Checking -> "Verificando"
    ModelStatus.Queued -> "Aguardando"
    is ModelStatus.Downloading -> progress?.let { "Baixando ${(it * 100).toInt()}%" } ?: "Baixando"
    ModelStatus.Verifying -> "Validando"
    ModelStatus.Ready -> "Pronto para uso"
    is ModelStatus.Failed -> "Falhou"
}

@Composable
internal fun BrandMark(size: Int = 38) {
    Image(
        painter = painterResource(Res.drawable.sattory_app_icon),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size * .28f).dp)),
    )
}
