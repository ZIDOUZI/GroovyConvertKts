package zdz.groovyconvertkts

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import java.io.File
import javax.swing.JFileChooser
import javax.swing.LookAndFeel
import javax.swing.UIManager
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.filechooser.FileSystemView.getFileSystemView
import androidx.compose.ui.window.AwtWindow
import java.awt.FileDialog

/**
 * 根据JFileChooser改造而来的compose元件
 * 尚未彻底完成,不过并未发现显著bug.可以类似用[AwtWindow]启动[FileDialog]一样,没有其他问题
 * @param[dir]文件选择器的起始目录
 * @param[title]文件选择器的标题
 * @param[mode]模式,读取或保存,应为[Mode]之一
 * @param[chooseMode]选择模式,单选或多选,文件,文件夹或混合,应为[ChooseMode]之一
 * @param[fileFilter]选择文件夹时为文件过滤器,其他就不知道了.格式为suffix
 * @param[dispose]关闭时进行的操作
 * @param[enableDrag]启用拖动,是[JFileChooser]里的功能,一起加进来了
 * @param[lookAndFeel]文件选择器外观,默认当前系统外观而不是java初始的老外观
 * @param[positiveResult]选择了确认后执行
 * @param[negativeResult]关闭或选择了取消后执行
 * @see[AwtWindow]
 * @see[JFileChooser]
 */
@Suppress("EXPERIMENTAL_API_USAGE")
@Composable
fun ExtendFileDialog(
    title: String = "untitled",
    dir: String? = null,
    mode: Mode = Mode.LOAD,
    chooseMode: ChooseMode = ChooseMode.FILE,
    fileFilter: FileFilter? = null,
    dispose: () -> Unit,
    enableDrag: Boolean = false,
    lookAndFeel: LookAndFeel? = null,
    negativeResult: (File) -> Unit = {},
    positiveResult: (File) -> Unit,
) { //TODO: 可能需要observable

    var result: Int

    lookAndFeel?.let { UIManager.setLookAndFeel(it) }
        ?: UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

    val j = JFileChooser(dir ?: getFileSystemView().defaultDirectory.path)
    j.dialogTitle = title
    j.isMultiSelectionEnabled = chooseMode.value % 2 != 0
    j.fileSelectionMode = chooseMode.value / 2
    j.dragEnabled = enableDrag
    if (chooseMode.value !in 2..3) {
        j.fileFilter = fileFilter
    }//TODO: 检测是否有异常

    GlobalScope.launch(Dispatchers.Swing) {
        result = when (mode) {
            Mode.LOAD -> j.showOpenDialog(null)
            Mode.SAVE -> j.showSaveDialog(null)//TODO: parent
        }


        when(result) {
            0 -> {
                val fileInfo = if (j.isMultiSelectionEnabled) j.selectedFiles else arrayOf(j.selectedFile)
                for (i in fileInfo) {
                    positiveResult(i)
                }
            }
            1 -> negativeResult (j.currentDirectory)
            -1 -> println("文件选择框返回错误")
        }
        dispose()
    }
}

interface ExtendFileDialog

enum class Mode(val value: Int) : ExtendFileDialog {
    LOAD(0),
    SAVE(1),
}

enum class ChooseMode(val value: Int) : ExtendFileDialog {
    FILE(0),
    FILES(1),
    FOLDER(2),
    FOLDERS(3),
    FILE_OR_FOLDER(4),
    FILES_OR_FOLDERS(5),
}

enum class Result(val value: Int) : ExtendFileDialog {
    CANCAL(0),
    APPROVE(1),
    ERROR(-1),
}
