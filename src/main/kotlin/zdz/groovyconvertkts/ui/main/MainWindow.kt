package zdz.groovyconvertkts.ui.main

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import zdz.groovyconvertkts.ui.theme.GCKTheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

@Preview
@Composable
fun App(ws: MainWindowState) {
    GCKTheme {
        MainScreen(ws)
    }
}

@Suppress("EXPERIMENTAL_API_USAGE")
fun main() = application {

    val ws = MainWindowState()
    val property = Properties()

    //加载参数
    try {
        property.load(FileInputStream("settings.properties"))
        ws.deleteFile = (property.getProperty("delete") ?: "true").toBoolean()
        ws.path = property.getProperty("path")
        ws.selectFolder = property.getProperty("multiple").toBoolean()
        ws.turnoffNotification = property.getProperty("notification").toBoolean()
    } catch (e: Exception) {
        try {
            val file = File("settings.properties")
            if (file.createNewFile()) println("文件创建成功！") else println("出错了，该文件已经存在。")
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }
        ws.deleteFile = true
        ws.path = null
        ws.selectFolder = false
        ws.turnoffNotification = false
    }

    Window(
        onCloseRequest = {
            GlobalScope.launch(Dispatchers.IO) {
                //保存参数
                try {
                    //正常终止前任务
                    val out = FileOutputStream("settings.properties")
                    property.setProperty("delete", ws.deleteFile.toString())
                    ws.path?.let { property.setProperty("path", it) }
                    property.setProperty("multiple", ws.selectFolder.toString())
                    property.setProperty("notification", ws.turnoffNotification.toString())

                    property.store(out, "change the property")
                    print("\n保存配置成功")
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            exitApplication()
        },
        title = "Gradle 转换 Kts",
        icon = painterResource("Kotlin.ico")
    ) {
        ws.rootComposeWindow = window
        App(ws)
    }
}
