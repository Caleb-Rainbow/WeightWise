package com.example.weight.ui.common

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * 滚轮式数字选择器，用于体重、身高等数值的录入与回填
 *
 * @param integerList 整数部分的可选值列表
 * @param decimalList 小数部分的可选值列表
 * @param onWeightChange 选择结果变化时回调
 * @param initialWeight 打开时回填的初始值
 * @param unit 单位文案
 */
@Composable
fun NumberSelector(
    integerList: List<Int>,
    decimalList: List<Int>,
    onWeightChange: (Double) -> Unit,
    initialWeight: Double? = null,
    unit: String
) {
    val integerPagerState = rememberPagerState { integerList.size }
    val decimalPagerState = rememberPagerState { decimalList.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(initialWeight) {
        initialWeight?.let {
            // 回填值可能超出列表范围（如历史数据），收敛到合法页码避免崩溃
            val initialInteger = it.toInt().coerceIn(integerList.first(), integerList.last())
            scope.launch {
                integerPagerState.animateScrollToPage(
                    initialInteger - integerList[0],
                    animationSpec = spring(stiffness = Spring.StiffnessVeryLow)
                )
            }
            scope.launch {
                // 浮点误差会让 0.1*10 变成 0.9999...，必须四舍五入而不是 toInt 截断，
                // 否则 0.1/0.3/0.7 等小数回填时会偏小一格
                decimalPagerState.animateScrollToPage(
                    decimalList.indexOf((it % 1 * 10).roundToInt()).coerceAtLeast(0),
                    animationSpec = spring(stiffness = Spring.StiffnessVeryLow)
                )
            }
        }
    }
    Row(modifier = Modifier.height(120.dp), verticalAlignment = Alignment.CenterVertically) {
        CustomVerticalPager(pagerState = integerPagerState, list = integerList) {
            val decimal = decimalList[decimalPagerState.currentPage] / 10.0
            onWeightChange(it + decimal)
        }
        Spacer(
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(color = Color.Gray)
        )
        CustomVerticalPager(pagerState = decimalPagerState, list = decimalList) {
            val integer = integerList[integerPagerState.currentPage]
            onWeightChange(integer + it.toDouble() / 10)
        }
        Text(
            modifier = Modifier.padding(start = 1.dp, bottom = 6.dp),
            text = unit,
            color = Color.Gray,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun CustomVerticalPager(pagerState: PagerState, list: List<Int>, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    val vibrator = remember {
        context.getSystemService(Vibrator::class.java)
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            onChange(list[page])
        }
    }
    val fling = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(100),
    )
    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 100,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = 40.dp)
    ) { page ->
        Text(modifier = Modifier.graphicsLayer {
            val pageOffset = (
                    (pagerState.currentPage - page) + pagerState
                        .currentPageOffsetFraction
                    ).absoluteValue

            alpha = lerp(
                start = 0.5f,
                stop = 1f,
                fraction = 1f - pageOffset.coerceIn(0f, 1f)
            )
            val scale = lerp(
                start = 1.1f,
                stop = 0.8f,
                fraction = pageOffset.coerceIn(0f, 1f)
            )
            scaleX = scale
            scaleY = scale
        }, text = "${list[page]}", fontSize = 20.sp)
    }
}
