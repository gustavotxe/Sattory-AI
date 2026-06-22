package org.sattory.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.sattory.app.llm.AndroidLocalLlm
import org.sattory.app.llm.UnavailableLocalLlm
import org.sattory.app.model.ModelFileSpec

class MainActivity : ComponentActivity() {
    private val localLlm by lazy {
        AndroidLocalLlm(ModelFileSpec.file(this).absolutePath)
    }
    private val modelRepository by lazy { (application as SattoryApplication).modelRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(localLlm, modelRepository)
        }
    }

    override fun onDestroy() {
        if (isFinishing) localLlm.close()
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(UnavailableLocalLlm("Pré-visualização sem modelo local"))
}
