package com.example.weight.ui.common

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LayeredComponent
import com.patrykandpatrick.vico.compose.common.component.Component
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Brush.Companion.verticalGradient
import java.util.Locale
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.text.DecimalFormat

fun BottomXDateFormatter(labels: List<String>): CartesianValueFormatter =
    CartesianValueFormatter { _, value, _ ->
        labels.getOrNull(value.toInt()) ?: "无日期"
    }

/**
 * 滑动窗口均值：窗口右移时增量加减，O(n) 完成且不逐点分配临时列表。
 * 数据不足一个窗口时返回空列表（调用方据此不画均线）。
 */
internal fun movingAverage(values: List<Double>, window: Int = 7): List<Double> {
    if (values.size < window) return emptyList()
    val result = ArrayList<Double>(values.size - window + 1)
    var sum = 0.0
    for (i in values.indices) {
        sum += values[i]
        if (i >= window) sum -= values[i - window]
        if (i >= window - 1) result.add(sum / window)
    }
    return result
}

/**
 * 目标体重参考线：横贯图表的虚线 + 右端标签，让「距离目标多远」在图上直接可见。
 * Vico 的 [com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine] 基于
 * LineComponent 绘制，不支持虚线，故仿其实现自绘（坐标换算逻辑一致）。
 */
internal data class TargetWeightLine(
    val y: Double,
    val color: Color,
    val label: TextComponent?,
    val labelText: String,
    val thickness: Dp = 1.5.dp,
    val dashLength: Dp = 6.dp,
    val gapLength: Dp = 4.dp,
) : Decoration {
    private val paint = Paint()

    override fun drawOverLayers(context: CartesianDrawingContext) {
        with(context) {
            val yRange = ranges.getYRange(null)
            if (yRange.length <= 0 || y !in yRange.minY..yRange.minY + yRange.length) return
            val canvasY =
                layerBounds.bottom - ((y - yRange.minY) / yRange.length).toFloat() * layerBounds.height
            paint.color = color
            paint.strokeWidth = thickness.pixels
            paint.pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.pixels, gapLength.pixels), 0f)
            canvas.drawLine(Offset(layerBounds.left, canvasY), Offset(layerBounds.right, canvasY), paint)
            if (label == null) return
            // 标签贴图层右缘、优先放线上方；线贴近顶部时翻到下方，避免被裁剪
            val labelHeight = label.getHeight(this, labelText)
            val fitsAbove = canvasY - thickness.pixels - labelHeight >= layerBounds.top
            label.draw(
                context = context,
                text = labelText,
                x = if (isLtr) layerBounds.right else layerBounds.left,
                y = if (fitsAbove) canvasY - thickness.pixels / 2 else canvasY + thickness.pixels / 2,
                // Horizontal.Start/End 指文本从锚点向哪侧展开：贴右缘需 Start（右缘对齐向左展开）
                horizontalPosition = if (isLtr) Position.Horizontal.Start else Position.Horizontal.End,
                verticalPosition = if (fitsAbove) Position.Vertical.Top else Position.Vertical.Bottom,
                maxWidth = layerBounds.width.toInt(),
            )
        }
    }
}

@Composable
internal fun rememberMarker(
    valueFormatter: DefaultCartesianMarker.ValueFormatter =
        DefaultCartesianMarker.ValueFormatter.default(),
    showIndicator: Boolean = true,
): CartesianMarker {
    val labelBackgroundShape = RoundedCornerShape(10)
    val labelBackground =
        rememberShapeComponent(
            fill = Fill(MaterialTheme.colorScheme.background),
            shape = labelBackgroundShape,
            strokeThickness = 1.dp,
            strokeFill = Fill(MaterialTheme.colorScheme.outline),
        )
    val label =
        rememberTextComponent(
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            padding = Insets(8.dp, 4.dp),
            background = labelBackground,
            minWidth = TextComponent.MinWidth.fixed(40.dp),
        )
    val indicatorFrontComponent =
        rememberShapeComponent(Fill(MaterialTheme.colorScheme.surface), RoundedCornerShape(10))
    val guideline = rememberAxisGuidelineComponent()
    // indicator 在 draw 阶段按系列颜色取组件：按颜色缓存，避免滚动时每帧新建 LayeredComponent
    val indicator: ((Color) -> Component)? =
        if (showIndicator) {
            remember(indicatorFrontComponent) {
                val cache = mutableMapOf<Color, LayeredComponent>()
                val indicatorOf: (Color) -> Component = { color: Color ->
                    cache.getOrPut(color) {
                        LayeredComponent(
                            back = ShapeComponent(Fill(color.copy(alpha = 0.15f)), RoundedCornerShape(10)),
                            front = LayeredComponent(
                                back = ShapeComponent(fill = Fill(color), shape = RoundedCornerShape(10)),
                                front = indicatorFrontComponent,
                                padding = Insets(5.dp),
                            ),
                            padding = Insets(10.dp),
                        )
                    }
                }
                indicatorOf
            }
        } else {
            null
        }
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = valueFormatter,
        indicator = indicator,
        indicatorSize = 36.dp,
        guideline = guideline,
    )
}

@Composable
fun BMIIndexChart(
    modifier: Modifier = Modifier,
    currentBMI: Double = 29.0
) {
    val bmiRanges = BMI.entries.toList() // 获取所有BMI枚举值
    val minBMI = bmiRanges.first().start
    val maxBMI = bmiRanges.last().end
    val totalBMIRange = maxBMI - minBMI
    val itemCoordinates = remember { mutableStateMapOf<Int, LayoutCoordinates>() }
    var rowWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        rowWidthPx = coordinates.size.width
                    }
            ) {
                bmiRanges.forEachIndexed { index, bmiEnum ->
                    val rangeWidth = bmiEnum.end - bmiEnum.start
                    val weight = rangeWidth / totalBMIRange
                    val leftRadius = if (index == 0) 100.dp else 0.dp
                    val rightRadius = if (index == bmiRanges.size - 1) 100.dp else 0.dp
                    BMIIndexItem(
                        modifier = Modifier
                            .weight(weight.toFloat())
                            .onGloballyPositioned { coordinates ->
                                itemCoordinates[index] = coordinates
                            },
                        color = bmiEnum.color,
                        leftRadius = leftRadius,
                        rightRadius = rightRadius
                    )
                    if (index < bmiRanges.size - 1) {
                        Spacer(Modifier.width(1.dp))
                    }
                }
            }
            if (rowWidthPx > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(top = 2.dp)
                ) {
                    bmiRanges.forEachIndexed { index, bmiEnum ->
                        if (index < bmiRanges.size - 1) {
                            val currentItemCoords = itemCoordinates[index]
                            val nextItemCoords = itemCoordinates[index + 1]
                            if (currentItemCoords != null && nextItemCoords != null) {
                                val spacerCenterX = (currentItemCoords.positionInParent().x + currentItemCoords.size.width + nextItemCoords.positionInParent().x) / 2f
                                val spacerCenterXDp = with(density) { spacerCenterX.toDp() }
                                val labelValue = bmiEnum.end // 当前区间的结束值就是分割点
                                Text(
                                    text = DecimalFormat("0.0").format(labelValue+0.1),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .absoluteOffset(x = spacerCenterXDp - 15.dp) // 微调位置，15dp是文字宽度的一半，使其居中
                                )
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.padding(top = 5.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                bmiRanges.forEach { bmiEnum ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Spacer(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(color = bmiEnum.color)
                                .width(10.dp)
                                .height(10.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(text = bmiEnum.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (rowWidthPx > 0) {
            var arrowXOffsetDp: Dp = 0.dp
            var accumulatedWidthPx = 0f
            for ((index, bmiEnum) in bmiRanges.withIndex()) {
                val currentItemCoords = itemCoordinates[index]
                if (currentItemCoords == null) continue
                val rangeWidthPx = currentItemCoords.size.width.toFloat()
                if (currentBMI >= bmiEnum.start && currentBMI <= bmiEnum.end) {
                    val progressInSection = (currentBMI - bmiEnum.start) / (bmiEnum.end - bmiEnum.start)
                    val offsetInCurrentRangePx = rangeWidthPx * progressInSection
                    arrowXOffsetDp = with(density) { (accumulatedWidthPx + offsetInCurrentRangePx).toFloat().toDp() }
                    break
                }
                accumulatedWidthPx += rangeWidthPx
                if (index < bmiRanges.size - 1) {
                    accumulatedWidthPx += with(density) { 1.dp.toPx() }
                }
            }
            val animateOffset by animateIntOffsetAsState(targetValue = IntOffset(
                x = with(density) { (arrowXOffsetDp - 12.dp).roundToPx() } , // 微调箭头中心
                y = with(density) {(-14).dp.roundToPx()}
            ))
            Icon(
                modifier = Modifier
                    .offset{animateOffset},
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BMIIndexItem(modifier: Modifier, color: Color = Color(0xff3aadcd), leftRadius: Dp = 0.dp, rightRadius: Dp = 0.dp) {
    Spacer(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = leftRadius, bottomStart = leftRadius, bottomEnd = rightRadius, topEnd = rightRadius))
            .background(color = color)
            .height(15.dp)
    )
}
enum class BMI(val start: Double, val end: Double, val label: String, val color: Color, val advice: String){
    LOW(start = 10.0, end = 18.4, label = "偏低", color = Color(0xff3aadcd), advice = "体重偏轻，注意均衡营养、适度增肌"),
    STANDARD(start = 18.5, end = 23.9, label = "标准", color = Color(0xff2abc6d), advice = "体重处于健康区间，继续保持"),
    OVERWEIGHT(start = 24.0, end = 27.9, label = "偏高", color = Color(0xffffb700), advice = "体重略超标准，留意饮食与运动"),
    OBESE(start = 28.0, end = 38.0, label = "过高", color = Color(0xffff8e00), advice = "超出健康范围较多，建议系统管理体重");

    companion object {
        fun fromBMIValue(bmi: Double): BMI? {
            return entries.find {
                if (it == LOW) bmi >= it.start && bmi <= it.end
                else bmi > it.start && bmi <= it.end
            }
        }
    }
}

@Preview
@Composable
private fun Preview(){
    Box (modifier = Modifier.background(Color.White)){
        BMIIndexChart(modifier = Modifier.padding(10.dp))
    }
}

/**
 * 体重趋势折线图：主线（渐变面积填充）+ 可选 7 日均线（细实线）+ 目标体重虚线。
 * 从首页抽出，供首页与报告页共用；marker 点击联动回调由调用方决定用途。
 */
@Composable
fun WeightChart(
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier,
    xLabels: List<String> = emptyList(),
    maxWeight: Double,
    minWeight: Double,
    lineColor: Color,
    showMovingAverage: Boolean = false,
    targetWeight: Double = 0.0,
    onMarkerClick: (Int) -> Unit = NoOpMarkerClick,
) {
    val movingAverageColor = MaterialTheme.colorScheme.secondary
    val targetLineColor = MaterialTheme.colorScheme.tertiary
    // 目标线标签带背景，避免和数据线重叠时看不清
    val targetLabelComponent = rememberTextComponent(
        style = TextStyle(color = targetLineColor, textAlign = TextAlign.Center),
        padding = Insets(6.dp, 2.dp),
        background = rememberShapeComponent(
            fill = Fill(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(6.dp),
        ),
    )
    val targetDecoration = if (targetWeight > 0) {
        remember(targetWeight, targetLineColor, targetLabelComponent) {
            TargetWeightLine(
                y = targetWeight,
                color = targetLineColor,
                label = targetLabelComponent,
                labelText = "目标 ${String.format(Locale.CHINA, "%.1f", targetWeight)}",
            )
        }
    } else {
        null
    }
    // 以下实例全部 memo 化：rememberCartesianChart 按参数键控 remember，
    // 传入新实例会让整张图表（含轴/marker）在每次无关重组时被重建
    val bottomFormatter = remember(xLabels) { BottomXDateFormatter(xLabels) }
    val decorations = remember(targetDecoration) { listOfNotNull(targetDecoration) }
    val rangeProvider = remember(maxWeight, minWeight) {
        CartesianLayerRangeProvider.fixed(maxY = maxWeight, minY = minWeight)
    }
    val startAxisTitle: (ExtraStore) -> CharSequence? = remember { { "体重" } }
    val valueFormatter = remember {
        // 有均线的点位同时显示当日体重与均值，颜色与各自曲线一致
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val points =
                (targets.firstOrNull() as? LineCartesianLayerMarkerTarget)?.points.orEmpty()
            val weightPoint = points.firstOrNull { it.entry.seriesIndex == 0 }
            val averagePoint = points.firstOrNull { it.entry.seriesIndex == 1 }
            when {
                weightPoint == null -> ""
                averagePoint == null ->
                    String.format(Locale.CHINA, "%.1fkg", weightPoint.entry.y)
                else -> buildAnnotatedString {
                    withStyle(SpanStyle(color = weightPoint.color, fontWeight = FontWeight.Bold)) {
                        append(String.format(Locale.CHINA, "%.1f", weightPoint.entry.y))
                    }
                    append("kg  均 ")
                    withStyle(SpanStyle(color = averagePoint.color, fontWeight = FontWeight.Bold)) {
                        append(String.format(Locale.CHINA, "%.1f", averagePoint.entry.y))
                    }
                }
            }
        }
    }
    val visibilityListener = remember(onMarkerClick) {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                super.onShown(marker, targets)
                targets.singleOrNull()?.let {
                    onMarkerClick(it.x.toInt())
                }
            }

            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                super.onUpdated(marker, targets)
                targets.singleOrNull()?.let {
                    onMarkerClick(it.x.toInt())
                }
            }
        }
    }
    CartesianChartHost(
        rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider =
                LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
                        areaFill =
                        LineCartesianLayer.AreaFill.single(
                            Fill(
                                verticalGradient(
                                    listOf(lineColor.copy(alpha = 0.4f), Color.Transparent)
                                )
                            )
                        ),
                    ),
                    // 7 日均线：细实线、无面积填充，与主线拉开视觉层级
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(movingAverageColor)),
                        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp),
                    ),
                ),
                rangeProvider = rangeProvider,
            ),
            startAxis = VerticalAxis.rememberStart(
                title = startAxisTitle,
                valueFormatter = CartesianValueFormatter.decimal(decimalCount = 2, suffix = "kg"),
                itemPlacer = remember { VerticalAxis.ItemPlacer.step(step = { 0.5 }) }),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
            decorations = decorations,
            marker = rememberMarker(valueFormatter = valueFormatter),
            markerVisibilityListener = visibilityListener,
        ),
        modelProducer = modelProducer,
        modifier = modifier.height(220.dp),
        scrollState = rememberVicoScrollState(scrollEnabled = true, initialScroll = Scroll.Absolute.End),
    )
}

/** 稳定的默认空回调：避免默认参数每次组合生成新 lambda 导致图表重建 */
private val NoOpMarkerClick: (Int) -> Unit = {}