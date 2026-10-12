package dev.eta.relayhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.eta.relayhub.ui.RelayApp
import dev.eta.relayhub.ui.RelayViewModel
import dev.eta.relayhub.ui.theme.RelayHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge 必须在 super.onCreate 之后调用，否则会被系统重新覆盖窗口设置
        enableEdgeToEdge()
        setContent {
            val vm: RelayViewModel = viewModel()
            // 只在主题真正变化时才重组外层；初始值用默认主题先出第一帧
            val prefs by vm.theme.collectAsStateWithLifecycle()
            RelayHubTheme(prefs = prefs) {
                RelayApp(vm)
            }
        }
    }
}
