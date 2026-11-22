package io.github.cyb3rl00m.prune.ui.components

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** テキスト (スクリプト、履歴) をシステムのファイル選択で保存したり、別のアプリに共有したりできるようにする。 */
class TextExporter(private val context: Context, private val launch: (String) -> Unit, private val pending: androidx.compose.runtime.MutableState<String?>) {
    fun save(fileName: String, text: String) {
        pending.value = text
        launch(fileName)
    }

    fun share(fileName: String, text: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, fileName).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun rememberTextExporter(onSaved: (Boolean) -> Unit = {}): TextExporter {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = pending.value
        pending.value = null
        if (uri != null && text != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
            }.getOrDefault(false)
            onSaved(ok)
        }
    }
    return remember(launcher) { TextExporter(context, { launcher.launch(it) }, pending) }
}
