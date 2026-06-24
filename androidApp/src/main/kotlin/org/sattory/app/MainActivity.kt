package org.sattory.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.sattory.app.llm.UnavailableLocalLlm
import org.sattory.app.model.ReadyModelRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(listOf((application as SattoryApplication).appModule))
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(sattoryModule(UnavailableLocalLlm("Pre-visualizacao sem modelo local"), ReadyModelRepository()))
}
