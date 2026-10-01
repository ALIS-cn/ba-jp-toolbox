package com.bluearchive.toolbox

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bluearchive.toolbox.data.prefs.AppPreferences
import com.bluearchive.toolbox.ui.ToolboxViewModel
import com.bluearchive.toolbox.ui.disclaimer.DisclaimerScreen
import com.bluearchive.toolbox.ui.nav.ToolboxScaffold
import com.bluearchive.toolbox.ui.theme.ToolboxTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ToolboxTheme {
                val vm: ToolboxViewModel = viewModel()
                val agreement by vm.agreementVersion.collectAsState()
                when (agreement) {
                    null -> LoadingView()
                    AppPreferences.DISCLAIMER_VERSION -> ToolboxScaffold(vm)
                    else -> DisclaimerScreen(
                        onAgree = vm::acceptDisclaimer,
                        onExit = { (this as? Activity)?.finish() },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
