package zdz.groovyconvertkts.ui.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Notification
import zdz.groovyconvertkts.ChooseMode
import zdz.groovyconvertkts.ExtendFileDialog
import zdz.groovyconvertkts.Title
import zdz.groovyconvertkts.core.convert
import zdz.groovyconvertkts.core.processFile
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun MainScreen(ws: MainWindowState) {

    var value by mutableStateOf("")

    /**
     * 处理array中的文件
     * @return 处理是否成功
     */
    fun process(file: File): Boolean {
        ws.path = file.path
        processFile(file)
        if (ws.deleteFile) file.delete()
        return true
    }

    Title(
        title = { Text(text = "Groovy 转换 kts", fontSize = 30.sp) },
        modifier = Modifier.padding(30.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = ws.deleteFile, onCheckedChange = { ws.deleteFile = it })
            Text("删除原始文件")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = ws.selectFolder, onCheckedChange = { ws.selectFolder = it })
            Text("选择文件夹")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = ws.turnoffNotification, onCheckedChange = { ws.turnoffNotification = it })
            Text("关闭通知")
        }



        Button(
            onClick = { ws.isAwaiting = true },
        ) {
            Text(text = if (ws.selectFolder) "选择文件夹" else "选择文件")
        }


        Button(onClick = { convert(value) }) {
            Text("转换输入框代码")
        }
        TextField(value = value, onValueChange = { value = it })

        Text(text = "tip: 选择文件夹时请选择src目录哦", color = Color.Gray, fontSize = 12.sp)
    }

    if (ws.isAwaiting) {
        ExtendFileDialog(
            title = if (ws.selectFolder) "选择文件夹" else "选择文件",
            dir = ws.path,
            dispose = { ws.isAwaiting = false },
            chooseMode = if (ws.selectFolder) ChooseMode.FOLDER else ChooseMode.FILES,
            fileFilter = FileNameExtensionFilter("Gradle构造文件(*.gradle)", "gradle"),
            positiveResult = {
                if (ws.selectFolder) {
                    try {
                        process(File("$it/build.gradle"))
                        process(File("$it/settings.gradle"))
                        process(File("$it/app/build.gradle"))
                    } catch (e: Exception) {
                        print(e.message)
                    }
                    ws.path = it.path
                    print(ws.path)
                } else {
                    process(it)
                    ws.path = it.path.substring(0..it.path.lastIndexOf("\\"))
                    print(ws.path)
                }
                Notification("Groovy转换kts", "转换成功")
            },
            negativeResult = { ws.path = it.path },
        )
    }
}
