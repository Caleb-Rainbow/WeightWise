package com.example.weight.data.widget

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.example.weight.widget.WeightWidget
import org.koin.core.annotation.Single

/**
 * 数据变更后刷新桌面小组件。记录增/删/改和备份导入完成时调用。
 * updateAll 是 suspend，调用方需在协程内执行；失败仅记日志不影响主流程。
 */
@Single
class WidgetUpdater(private val application: Application) {

    suspend fun notifyDataChanged() {
        try {
            WeightWidget().updateAll(application)
        } catch (e: Exception) {
            // 小组件尚未添加到桌面等场景下可能抛错，属预期，不打断业务流程
            android.util.Log.w("WidgetUpdater", "刷新小组件失败", e)
        }
    }
}
