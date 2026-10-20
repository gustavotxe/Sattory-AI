package org.sattory.app.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberPdfPicker(onSelected: (String) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onSelected)
    return remember {
        {
            SwingUtilities.invokeLater {
                val chooser = JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter("PDF", "pdf")
                    isAcceptAllFileFilterUsed = false
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    callback.value(chooser.selectedFile.absolutePath)
                }
            }
        }
    }
}
