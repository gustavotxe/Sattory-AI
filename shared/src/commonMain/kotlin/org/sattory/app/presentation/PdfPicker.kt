package org.sattory.app.presentation

import androidx.compose.runtime.Composable

@Composable
expect fun rememberPdfPicker(onSelected: (String) -> Unit): () -> Unit
