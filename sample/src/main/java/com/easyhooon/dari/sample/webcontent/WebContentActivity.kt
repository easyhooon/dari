package com.easyhooon.dari.sample.webcontent

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.web.WebContent
import com.easyhooon.dari.sample.ui.theme.DariTheme

/**
 * Hosts a page through androidx.web [WebContent] (issue #311). The engine lives in [WebContentModel],
 * so rotating the device re-attaches the same page to a new WebContentView.
 */
class WebContentActivity : ComponentActivity() {

    private val model: WebContentModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DariTheme {
                Scaffold { padding ->
                    val modifier = Modifier.fillMaxSize().padding(padding)
                    val webContent = model.webContent
                    if (webContent == null) {
                        Text("WebFeature.WEB_CONTENT isn't supported by this device's WebView.", modifier)
                    } else {
                        WebContentHost(webContent, model, modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun WebContentHost(webContent: WebContent, model: WebContentModel, modifier: Modifier) {
    key(webContent) {
        AndroidView(
            modifier = modifier,
            factory = { context -> model.attach(context) },
            // The view must leave the hierarchy before WebContent.detach().
            onRelease = { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                model.detach()
            },
        )
    }
}
