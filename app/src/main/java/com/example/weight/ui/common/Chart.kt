package com.example.weight.ui.common

import android.text.Layout
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
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.compose.common.shape.markerCorneredShape
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.LayeredComponent
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.text.DecimalFormat

class BottomXDateFormatter(private val labels: List<String>) : CartesianValueFormatter {
    override fun format(
        context: CartesianMeasuringContext,
        value: Double,
        verticalAxisPosition: Axis.Position.Vertical?
    ): CharSequence {
        return labels.getOrNull(value.toInt()) ?: "无日期"
    }
}

@Composable
internal fun rememberMarker(
    valueFormatter: DefaultCartesianMarker.ValueFormatter =
        DefaultCartesianMarker.ValueFormatter.default(),
    showIndicator: Boolean = true,
): CartesianMarker {
    val labelBackgroundShape = markerCorneredShape(CorneredShape.Corner.Rounded)
    val labelBackground =
        rememberShapeComponent(
            fill = fill(MaterialTheme.colorScheme.background),
            shape = labelBackgroundShape,
            strokeThickness = 1.dp,
            strokeFill = fill(MaterialTheme.colorScheme.outline),
        )
    val label =
        rememberTextComponent(
            color = MaterialTheme.colorScheme.onSurface,
            textAlignment = Layout.Alignment.ALIGN_CENTER,
            padding = insets(8.dp, 4.dp),
            background = labelBackground,
            minWidth = TextComponent.MinWidth.fixed(40f),
        )
    val indicatorFrontComponent =
        rememberShapeComponent(fill(MaterialTheme.colorScheme.surface), CorneredShape.Pill)
    val guideline = rememberAxisGuidelineComponent()
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = valueFormatter,
        indicator =
            if (showIndicator) {
                { color ->
                    LayeredComponent(
                        back = ShapeComponent(fill(color.copy(alpha = 0.15f)), CorneredShape.Pill),
                        front =
                            LayeredComponent(
                                back = ShapeComponent(fill = fill(color), shape = CorneredShape.Pill),
                                front = indicatorFrontComponent,
                                padding = insets(5.dp),
                            ),
                        padding = insets(10.dp),
                    )
                }
            } else {
                null
            },
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
                                    color = Color.DarkGray,
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
                        Text(text = bmiEnum.label, fontSize = 12.sp, color = Color.DarkGray)
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
                tint = Color.Black
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
enum class BMI(val start: Double, val end: Double, val label: String,val color: Color){
    LOW(start = 10.0, end = 18.4, label = "偏低", color = Color(0xff3aadcd)),
    STANDARD(start = 18.5, end = 23.9, label = "标准", color = Color(0xff2abc6d)),
    OVERWEIGHT(start = 24.0, end = 27.9, label = "偏高", color = Color(0xffffb700)),
    OBESE(start = 28.0, end = 38.0, label = "过高", color = Color(0xffff8e00));

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