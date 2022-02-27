package zdz.groovyconvertkts.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow

class MainWindowState {
    var deleteFile by mutableStateOf(true)
    var selectFolder by mutableStateOf(false)
    var isAwaiting by mutableStateOf(false)
    var turnoffNotification by mutableStateOf(false)
    var path: String? = null
    lateinit var rootComposeWindow: ComposeWindow
}