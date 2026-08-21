package com.example.weight.widget

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.example.weight.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

/**
 * 体重桌面小组件：当前体重 + 较 7 天变化 + 目标进度条 + 距目标，宽卡附带近 7 天趋势线、
 * 7 天均值与底部信息条（BMI 区间红绿灯 / 连续打卡 / 趋势预测）。
 * 主区域点击直达记体重弹窗，进度条/趋势线点击直达报告页；超过 3 天未记录时首行给琥珀提醒。
 * 记录增删改、目标体重调整、备份导入后由 WidgetUpdater 主动刷新。
 */
class WeightWidget : GlanceAppWidget(errorUiLayout = R.layout.weight_widget_error) {

    /** 响应式两档：约 2x2 与 4x2 */
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(140.dp, 140.dp), DpSize(280.dp, 140.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // provideGlance 在主线程执行，Room 查询必须切 IO
        val data = try {
            withContext(Dispatchers.IO) {
                GlobalContext.get().get<com.example.weight.data.widget.WidgetRepository>().load()
            }
        } catch (e: Exception) {
            Log.w(TAG, "小组件数据加载失败", e)
            null
        }
        provideContent { WeightWidgetContent(data) }
    }

    companion object {
        private const val TAG = "WeightWidget"
    }
}
