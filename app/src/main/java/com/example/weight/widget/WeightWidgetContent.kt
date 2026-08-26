package com.example.weight.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.background
import androidx.glance.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColorProvider
import com.example.weight.MainActivity
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.data.widget.WeightWidgetData
import com.example.weight.ui.common.BMI
import com.example.weight.ui.theme.ThemePreset
import java.util.Locale
import kotlin.math.abs

/** 通知/小组件深链参数：主区域点击弹记体重对话框，进度/趋势区点击直达报告页 */
private val openAddDialogKey = ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_ADD_DIALOG)
private val openReportKey = ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_REPORT)

/** 涨跌配色口径同 ui/record/WeightTrendColors：降为利好绿、升为提醒红（昼夜两套） */
private val DecreaseColor = DayNightColorProvider(day = Color(0xFF378646), night = Color(0xFF87D98F))
private val IncreaseColor = DayNightColorProvider(day = Color(0xFFD44439), night = Color(0xFFFFB4AA))
/** 超过 [STALE_REMIND_DAYS] 天未记录时的琥珀提醒色，口径同饮食域琥珀 */
private val StaleColor = DayNightColorProvider(day = Color(0xFF9B7000), night = Color(0xFFF8BD42))
/** 进度条的底轨色，口径同 IntakeRingColors.Track */
private val TrackColor = DayNightColorProvider(day = Color(0xFFE3EAF0), night = Color(0xFF3A4048))

/** 超过该天数未记录时，「较 7 天前」参考价值下降，变化行改为陈旧提醒 */
private const val STALE_REMIND_DAYS = 3
private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * BMI 区间色，口径同 ui/common/Chart.kt 的 [bmiColor]：偏低跟随所选主题 primary，
 * 其余并入全局语义绿/琥珀/红，夜间取深色变体
 */
private fun bmiBandColor(band: BMI, preset: ThemePreset): ColorProvider = when (band) {
    BMI.LOW -> DayNightColorProvider(day = preset.light.primary, night = preset.dark.primary)
    BMI.STANDARD -> DayNightColorProvider(day = Color(0xFF378646), night = Color(0xFF87D98F))
    BMI.OVERWEIGHT -> DayNightColorProvider(day = Color(0xFF9B7000), night = Color(0xFFF8BD42))
    BMI.OBESE -> DayNightColorProvider(day = Color(0xFFD44439), night = Color(0xFFFFB4AA))
}

/** 趋势线逻辑尺寸与位图倍率：110x64dp，3x 渲染保证低密度屏不糊 */
private const val SPARKLINE_WIDTH_DP = 110
private const val SPARKLINE_HEIGHT_DP = 64
private const val SPARKLINE_SCALE = 3

@Composable
fun WeightWidgetContent(data: WeightWidgetData?, preset: ThemePreset) {
    // Responsive 模式下返回命中的断点尺寸，据此切换紧凑/完整布局
    val size = LocalSize.current
    // 趋势线是预渲染位图，昼夜色无法交给宿主解析，需在组合期按系统深色模式自行取色
    val isNight = LocalContext.current.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    GlanceTheme(colors = androidx.glance.material3.ColorProviders(preset.light, preset.dark)) {
        val openAdd = actionStartActivity<MainActivity>(
            parameters = actionParametersOf(openAddDialogKey to true),
        )
        val openReport = actionStartActivity<MainActivity>(
            parameters = actionParametersOf(openReportKey to true),
        )
        // 1.1.1 无 background(ImageProvider)，用所选主题昼夜 surface + cornerRadius 达成圆角卡片
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(day = preset.light.surface, night = preset.dark.surface)
                .cornerRadius(16.dp)
                .clickable(openAdd)
                .padding(14.dp),
        ) {
            if (data?.currentWeight == null) {
                WidgetEmptyContent()
            } else {
                WidgetDataContent(
                    data = data,
                    compact = size.width < 200.dp,
                    isNight = isNight,
                    preset = preset,
                    openReport = openReport,
                )
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
private fun WidgetDataContent(
    data: WeightWidgetData,
    compact: Boolean,
    isNight: Boolean,
    preset: ThemePreset,
    openReport: Action,
) {
    val current = data.currentWeight ?: return
    val staleDays = ((System.currentTimeMillis() - data.updatedAt) / DAY_MS).toInt()
    if (compact) {
        // 紧凑卡（约 2x2）：纵向堆叠，不放趋势线
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLine(data = data, current = current, staleDays = staleDays)
            WeightRow(current = current, compact = true)
            data.progressPercent?.let { percent ->
                ProgressRow(percent = percent, barWidth = 48.dp, openReport = openReport)
            }
            if (data.targetWeight > 0) {
                TargetText(targetWeight = data.targetWeight)
            }
        }
    } else {
        // 宽卡（约 4x2）：左右分栏 + 全宽底部信息条，填满纵向空间
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopLine(data = data, current = current, staleDays = staleDays)
                    WeightRow(current = current, compact = false)
                    data.progressPercent?.let { percent ->
                        ProgressRow(percent = percent, barWidth = 68.dp, openReport = openReport)
                    }
                    if (data.targetWeight > 0) {
                        TargetText(targetWeight = data.targetWeight, current = current)
                    }
                }
                if (data.dailyWeights.size >= 2) {
                    // 右列：加高趋势线 + 7 天均值，点击直达报告页
                    Column(
                        modifier = GlanceModifier.padding(start = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SparklineImage(points = data.dailyWeights, isNight = isNight, preset = preset, openReport = openReport)
                        Text(
                            text = String.format(
                                Locale.CHINA, "7天均 %.1f", data.dailyWeights.map { it.minWeight }.average()
                            ),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
            }
            BottomStrip(data = data, preset = preset)
        }
    }
}

/** 首行：长期未记录给琥珀提醒，否则展示较 7 天前变化，起步阶段给引导文案 */
@Composable
private fun TopLine(data: WeightWidgetData, current: Double, staleDays: Int) {
    when {
        staleDays >= STALE_REMIND_DAYS -> Text(
            text = if (staleDays > 99) "已 99+ 天未记录" else "已 $staleDays 天未记录",
            style = TextStyle(color = StaleColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        data.baselineWeight != null -> DeltaText(delta = current - data.baselineWeight)
        else -> Text(
            text = "继续记录看趋势",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}

@Composable
private fun WeightRow(current: Double, compact: Boolean) {
    // 卡片高度由桌面决定（2格高约110~150dp），底对齐让 kg 与数字基线一致
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
}

/** 进度区：点击直达报告页。Glance 1.1.1 无 fillMaxWidth(fraction)，用固定宽度胶囊表达进度 */
@Composable
private fun ProgressRow(percent: Int, barWidth: Dp, openReport: Action) {
    Row(
        modifier = GlanceModifier.clickable(openReport),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val filled = percent.coerceIn(0, 100) / 100f
        Box(
            modifier = GlanceModifier
                .width(barWidth)
                .height(6.dp)
                .cornerRadius(3.dp)
                .background(TrackColor),
        ) {
            if (filled > 0f) {
                Box(
                    modifier = GlanceModifier
                        .width(barWidth * filled)
                        .height(6.dp)
                        .cornerRadius(3.dp)
                        .background(GlanceTheme.colors.primary),
                ) {}
            }
        }
        Text(
            text = " $percent%",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}

/** 紧凑卡显示目标值；传 current 时（宽卡）改为更有行动力的距目标/已达标，口径同首页「距离目标还有 N kg」 */
@Composable
private fun TargetText(targetWeight: Double, current: Double? = null) {
    val text = if (current == null) {
        String.format(Locale.CHINA, "目标 %.1f kg", targetWeight)
    } else {
        val diff = abs(targetWeight - current)
        if (diff < 0.05) "已达标 🎉"
        else String.format(Locale.CHINA, "距目标 %.1f kg", diff)
    }
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
    )
}

/** 底部信息条：BMI 区间（口径同 ui/common/Chart.kt 的 bmiColor）+ 连续打卡 + 趋势预测 */
@Composable
private fun BottomStrip(data: WeightWidgetData, preset: ThemePreset) {
    val variant = GlanceTheme.colors.onSurfaceVariant
    val segments = buildList {
        data.bmi?.let { bmi ->
            add("BMI ${String.format(Locale.CHINA, "%.1f", bmi)}" to variant)
            BMI.fromBMIValue(bmi)?.let { band ->
                add(band.label to bmiBandColor(band, preset))
            }
        }
        if (data.currentStreak > 0) {
            add("🔥 连续${data.currentStreak}天" to variant)
        }
        data.estimatedDays?.let { days ->
            add("预计 ${days}天" to variant)
        }
    }
    if (segments.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        segments.forEachIndexed { index, (text, color) ->
            if (index > 0) {
                Text(
                    text = " · ",
                    style = TextStyle(color = variant, fontSize = 11.sp),
                )
            }
            Text(
                text = text,
                style = TextStyle(color = color, fontSize = 11.sp),
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

/** 近 7 天趋势线：Glance 无画布组件，预渲染位图后经 ImageProvider(bitmap) 上屏 */
@Composable
private fun SparklineImage(
    points: List<DailyMinWeight>,
    isNight: Boolean,
    preset: ThemePreset,
    openReport: Action,
) {
    // 趋势线取所选主题的昼夜 primary
    val lineColor = (if (isNight) preset.dark.primary else preset.light.primary).toArgb()
    val bitmap = remember(points, isNight, preset) {
        drawSparkline(
            weights = points.map { it.minWeight },
            widthPx = SPARKLINE_WIDTH_DP * SPARKLINE_SCALE,
            heightPx = SPARKLINE_HEIGHT_DP * SPARKLINE_SCALE,
            lineColor = lineColor,
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = "近 7 天体重趋势",
        modifier = GlanceModifier
            .width(SPARKLINE_WIDTH_DP.dp)
            .height(SPARKLINE_HEIGHT_DP.dp)
            .clickable(openReport),
        contentScale = ContentScale.FillBounds,
    )
}

/** 折线 + 底部渐变面积 + 末端当前点；单点或全平等退化场景由调用方过滤（size >= 2 才绘制） */
private fun drawSparkline(weights: List<Double>, widthPx: Int, heightPx: Int, lineColor: Int): Bitmap {
    val bitmap = createBitmap(widthPx, heightPx)
    val canvas = Canvas(bitmap)
    val min = weights.min()
    val max = weights.max()
    val flat = max - min < 1e-9
    val pad = 12f
    val stepX = (widthPx - 2 * pad) / (weights.size - 1)
    val xs = weights.indices.map { pad + it * stepX }
    val ys = weights.map { w ->
        if (flat) {
            heightPx / 2f
        } else {
            pad + (1f - ((w - min) / (max - min)).toFloat()) * (heightPx - 2 * pad)
        }
    }
    val linePath = Path().apply {
        moveTo(xs.first(), ys.first())
        xs.indices.drop(1).forEach { lineTo(xs[it], ys[it]) }
    }
    val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 0f, heightPx.toFloat(),
            argbWithAlpha(lineColor, 66),
            argbWithAlpha(lineColor, 0),
            Shader.TileMode.CLAMP,
        )
    }
    val areaPath = Path(linePath).apply {
        lineTo(xs.last(), heightPx.toFloat())
        lineTo(xs.first(), heightPx.toFloat())
        close()
    }
    canvas.drawPath(areaPath, areaPaint)
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(linePath, linePaint)
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
    canvas.drawCircle(xs.last(), ys.last(), 9f, dotPaint)
    return bitmap
}

/** 等价 ColorUtils.setAlphaComponent；本项目 SDK 平台 jar 被裁剪不含该类，只能手写位运算 */
private fun argbWithAlpha(argb: Int, alpha: Int): Int = (argb and 0x00FFFFFF) or (alpha shl 24)
