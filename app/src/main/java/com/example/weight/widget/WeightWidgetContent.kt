package com.example.weight.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColorProvider
import com.example.weight.MainActivity
import com.example.weight.data.widget.WeightWidgetData
import com.example.weight.ui.theme.darkScheme
import com.example.weight.ui.theme.lightScheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 通知/小组件深链参数：打开后直接弹记体重对话框 */
private val openAddDialogKey = ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_ADD_DIALOG)

/** 涨跌配色与记录页交通灯口径一致：降为利好绿、升为提醒红（昼夜两套） */
private val DecreaseColor = DayNightColorProvider(day = Color(0xFF4CAF50), night = Color(0xFF81C784))
private val IncreaseColor = DayNightColorProvider(day = Color(0xFFEF5350), night = Color(0xFFEF9A9A))
/** 进度点阵的底轨色 */
private val TrackColor = DayNightColorProvider(day = Color(0xFFB9C2CC), night = Color(0xFF3A4048))

/** 小组件背景色：取自 ui/theme/Color.kt 的昼夜 surface */
private const val BackgroundDay = 0xFFF7F9FF
private const val BackgroundNight = 0xFF101418

@Composable
fun WeightWidgetContent(data: WeightWidgetData?) {
    // Responsive 模式下返回命中的断点尺寸，据此切换紧凑/完整布局
    val size = LocalSize.current
    GlanceTheme(colors = androidx.glance.material3.ColorProviders(lightScheme, darkScheme)) {
        val openAdd = actionStartActivity<MainActivity>(
            parameters = actionParametersOf(openAddDialogKey to true),
        )
        // 1.1.1 无 background(ImageProvider)，用昼夜色 + cornerRadius 达成圆角卡片
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(day = Color(BackgroundDay), night = Color(BackgroundNight))
                .cornerRadius(16.dp)
                .clickable(openAdd)
                .padding(14.dp),
        ) {
            if (data?.currentWeight == null) {
                WidgetEmptyContent()
            } else {
                WidgetDataContent(data = data, compact = size.width < 200.dp)
            }
        }
    }
}

@Composable
private fun WidgetEmptyContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "⚖️", style = TextStyle(fontSize = 26.sp))
        Text(
            text = "开始记录体重吧",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = "点击记第一笔",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}

@Composable
private fun WidgetDataContent(data: WeightWidgetData, compact: Boolean) {
    val current = data.currentWeight ?: return
    // 垂直居中：卡片高度由桌面决定（2格高约110~150dp），顶对齐会留出大片空白
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 变化行：有 7 天基准才展示，起步阶段给引导文案
        if (data.baselineWeight != null) {
            DeltaText(delta = current - data.baselineWeight)
        } else {
            Text(
                text = "继续记录看趋势",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = String.format(Locale.CHINA, "%.1f", current),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (compact) 30.sp else 34.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = " kg",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        }

        data.progressPercent?.let { percent ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressDots(percent = percent, fillColor = GlanceTheme.colors.primary)
                Text(
                    text = " $percent%",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }
        }

        if (!compact && data.targetWeight > 0) {
            Text(
                text = String.format(Locale.CHINA, "目标 %.1f kg", data.targetWeight),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun DeltaText(delta: Double) {
    val text = when {
        abs(delta) < 0.05 -> "与 7 天前持平"
        delta < 0 -> String.format(Locale.CHINA, "↓ %.1f kg · 较7天前", abs(delta))
        else -> String.format(Locale.CHINA, "↑ %.1f kg · 较7天前", delta)
    }
    val color: ColorProvider = when {
        abs(delta) < 0.05 -> GlanceTheme.colors.onSurfaceVariant
        delta < 0 -> DecreaseColor
        else -> IncreaseColor
    }
    Text(
        text = text,
        style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    )
}

/** 10 点进度条：Glance 不支持 LinearProgressIndicator，用点阵表达 10% 粒度 */
@Composable
private fun ProgressDots(percent: Int, fillColor: ColorProvider) {
    val filled = (percent.coerceIn(0, 100) / 10.0).roundToInt()
    Row {
        repeat(10) { index ->
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .cornerRadius(4.dp)
                    .background(
                        day = if (index < filled) Color(0xFF2B638B) else Color(0xFFB9C2CC),
                        night = if (index < filled) Color(0xFF98CCF9) else Color(0xFF3A4048),
                    ),
            ) {}
            if (index < 9) {
                Box(modifier = GlanceModifier.width(2.dp).height(8.dp)) {}
            }
        }
    }
}
