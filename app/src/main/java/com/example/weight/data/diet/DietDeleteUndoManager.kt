package com.example.weight.data.diet

import com.example.weight.util.ImageCompressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

/**
 *@description: 饮食删除撤销管理器（应用级单例）。
 *
 *               机制（评审决策 #22 的落地细化）：记录行立即删除（DB/今日合计即时一致，
 *               无需双 Tab 同源过滤），图片文件延后到撤销窗口关闭才删；撤销=原 id 重插
 *               （Room @Insert 保留非零主键，行已删无冲突），零保真损失。
 *
 *               挂应用级 scope 而非 viewModelScope：撤销窗口内返回主屏再进来，
 *               待撤销栈仍在，SnackBar 重新弹出，撤销依然有效。
 *               进程死亡最坏情况：记录已删、图片成孤儿文件（体积小，可接受降级）。
 *@author: 杨帅林
 *@create: 2026/8/21
 **/
@Single
class DietDeleteUndoManager(
    private val dietRecordDao: DietRecordDao,
    private val appScope: CoroutineScope,
) {

    private val _pendingRecords = MutableStateFlow<List<DietRecord>>(emptyList())

    /** 待撤销记录栈；UI 据此弹「已删除 N 条 · 撤销」SnackBar */
    val pendingRecords: StateFlow<List<DietRecord>> = _pendingRecords.asStateFlow()

    /** 记录立即删除，图片删除挂起等待撤销窗口结果 */
    fun deleteWithUndo(record: DietRecord) {
        appScope.launch {
            dietRecordDao.delete(record)
            _pendingRecords.update { it + record }
        }
    }

    /** 撤销：整批原 id 重插，取消图片删除 */
    fun undoAll() {
        val records = _pendingRecords.value
        if (records.isEmpty()) return
        _pendingRecords.value = emptyList()
        appScope.launch { dietRecordDao.insertAll(records) }
    }

    /** 撤销窗口关闭（SnackBar 超时/被顶掉且未点撤销）：提交图片删除并清空待撤销栈 */
    fun commitPending() {
        val records = _pendingRecords.value
        if (records.isEmpty()) return
        _pendingRecords.value = emptyList()
        appScope.launch(Dispatchers.IO) {
            records.forEach { ImageCompressor.deleteImage(it.imageUri) }
        }
    }
}
