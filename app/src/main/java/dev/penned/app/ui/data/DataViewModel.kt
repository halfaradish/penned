package dev.penned.app.ui.data

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.penned.app.PennedApplication
import dev.penned.app.data.db.entity.ImportBatchEntity
import dev.penned.app.data.importer.ImportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 数据管理（导入/导出/批次）。文件选择用 SAF，UI 不需要存储权限。
 */
class DataViewModel(private val app: PennedApplication) : ViewModel() {

    private val repository = ImportRepository.from(app)

    /** 一次性提示（Snackbar）。 */
    val message = MutableStateFlow<String?>(null)

    val busy = MutableStateFlow(false)

    val batches: StateFlow<List<ImportBatchEntity>> = repository.observeBatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 批次明细（钻取页）。 */
    fun batch(batchId: String) = repository.observeBatch(batchId)

    fun batchTransactions(batchId: String) = repository.observeBatchTransactions(batchId)

    fun importYimuXls(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            busy.value = true
            try {
                val bytes = resolver.readBytes(uri)
                val fileName = queryDisplayName(resolver, uri) ?: "yimu.xls"
                val result = repository.importYimuXls(fileName, bytes)
                message.value = "导入成功 ${result.imported} 条" +
                    (result.skipped.takeIf { it > 0 }?.let { "，跳过 ${it} 行（${result.problemSummary}）" } ?: "")
            } catch (e: Exception) {
                message.value = "导入失败：${e.message}"
            } finally {
                busy.value = false
            }
        }
    }

    fun importCsv(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            busy.value = true
            try {
                val content = resolver.readText(uri)
                val fileName = queryDisplayName(resolver, uri) ?: "backup.csv"
                val result = repository.importCsv(fileName, content)
                message.value = "导入成功 ${result.imported} 条"
            } catch (e: Exception) {
                message.value = "导入失败：${e.message}"
            } finally {
                busy.value = false
            }
        }
    }

    fun exportCsv(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            busy.value = true
            try {
                val csv = repository.exportCurrentBookCsv()
                resolver.writeText(uri, csv)
                message.value = "导出完成"
            } catch (e: Exception) {
                message.value = "导出失败：${e.message}"
            } finally {
                busy.value = false
            }
        }
    }

    fun deleteBatch(batchId: String) {
        viewModelScope.launch {
            try {
                repository.deleteBatch(batchId)
                message.value = "批次已删除"
            } catch (e: Exception) {
                message.value = "删除失败：${e.message}"
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }

    private suspend fun ContentResolver.readBytes(uri: Uri): ByteArray =
        openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("无法读取文件")

    private suspend fun ContentResolver.readText(uri: Uri): String =
        openInputStream(uri)?.use { it.readBytes().toString(charset("UTF-8")) } ?: throw IllegalStateException("无法读取文件")

    private suspend fun ContentResolver.writeText(uri: Uri, content: String) {
        openOutputStream(uri)?.use { it.write(content.toByteArray(charset("UTF-8"))) }
            ?: throw IllegalStateException("无法写入文件")
    }
}
