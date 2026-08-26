package com.example.weight.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.DailyStatMode
import com.example.weight.data.record.DailyWeight
import com.example.weight.data.record.Record
import com.example.weight.ui.common.WeightChart
import com.example.weight.ui.common.SectionHeader
import com.example.weight.ui.common.StatusPill
import com.example.weight.ui.common.WeightWiseDimens
import com.example.weight.ui.common.movingAverage
import com.example.weight.ui.diet.QuickAddSheet
import com.example.weight.util.GoalProgressCalculator
import com.example.weight.util.StreakInfo
import com.example.weight.util.TimeUtils
import com.example.weight.util.WeightPredictor
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.common.vicoTheme
import org.koin.androidx.compose.koinViewModel
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.absoluteValue

internal const val HERO_RING_TEST_TAG = "hero_ring"


/** 首次进入、数据库尚未发出第一份数据时的短暂加载态 */
@Composable
internal fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

/** 无任何记录时的引导空状态：文案 + 主动作，而不是满屏 0.0 */
@Composable
internal fun EmptyContent(onAddRecord: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.MonitorWeight,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "开始记录体重吧", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "每天称一称，看见变化的发生",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddRecord, modifier = Modifier.height(48.dp)) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "记第一笔体重")
        }
    }
}

/** 首页的两条高频任务。用不对称比例强调称重，避免所有入口等权的图标宫格。 */
@Composable
internal fun DashboardQuickActions(
    modifier: Modifier = Modifier,
    onAddRecord: () -> Unit,
    onDietQuickAdd: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier
                .weight(1.25f)
                .fillMaxSize()
                .clickable(role = Role.Button, onClickLabel = "记录体重", onClick = onAddRecord),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 28.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(28.dp))
                Column {
                    Text("记录体重", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("今天还没称？现在记一笔", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Surface(
            modifier = Modifier
                .weight(0.75f)
                .fillMaxSize()
                .clickable(role = Role.Button, onClickLabel = "记一餐", onClick = onDietQuickAdd),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(26.dp))
                Column {
                    Text("记一餐", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("拍照或文字", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 杂志式章节头：编号负责节奏，大标题负责扫描。 */
@Composable
internal fun DashboardSectionTitle(
    index: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            text = index,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GoalProgressContent(
    modifier: Modifier,
    currentRecord: DailyWeight?,
    firstRecord: Record?,
    recentDailyWeights: List<DailyWeight>
) {
    currentRecord?.let {
        val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
        val configuredStartWeight by LocalStorageData.startWeight.collectAsStateWithLifecycle()
        // 手动设置的起始体重优先，未设置时跟随第一条记录；设置页修改后此处实时重组
        val startWeight =
            GoalProgressCalculator.effectiveStartWeight(configuredStartWeight, firstRecord?.weight)
                ?: 0.0
        val currentWeight = it.value
        if (targetWeight > 0) {
            val goalReached = currentWeight <= targetWeight

            // 核心逻辑：计算从起始到目标的进度百分比（算法与桌面小组件共用）
            val progress = remember(startWeight, currentWeight, targetWeight) {
                GoalProgressCalculator.progress(startWeight, currentWeight, targetWeight)
            }
            // 使用 animateFloatAsState 为进度条增加平滑的动画效果
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                label = "目标进度",
            )
            // 基于近 90 天每日代表值的加权回归趋势估算剩余天数，
            // 趋势停滞、反向或数据不足时返回 null，不显示天数
            val remainingDays = remember(recentDailyWeights, currentWeight, targetWeight) {
                WeightPredictor.estimateDaysToTarget(recentDailyWeights, currentWeight, targetWeight)
            }

            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(topStart = 10.dp, topEnd = 30.dp, bottomStart = 30.dp, bottomEnd = 10.dp),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.09f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GoalRing(
                        progress = animatedProgress,
                        modifier = Modifier.size(78.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (goalReached) "目标已达成" else "目标进度",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f),
                        )
                        Spacer(Modifier.height(3.dp))
                        val remainingWeight = (currentWeight - targetWeight).absoluteValue
                        Text(
                            text = if (goalReached) {
                                val lostWeight = startWeight - currentWeight
                                if (lostWeight > 0) "已减轻 ${String.format(Locale.CHINA, "%.1f", lostWeight)} kg"
                                else "稳住现在的节奏"
                            } else {
                                "还差 ${String.format(Locale.CHINA, "%.1f", remainingWeight)} kg"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                append("起点 ${String.format(Locale.CHINA, "%.1f", startWeight)}  ·  目标 ${String.format(Locale.CHINA, "%.1f", targetWeight)}")
                                if (!goalReached && remainingDays != null) append("  ·  预计 $remainingDays 天")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.68f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GoalRing(progress: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
    val indicator = MaterialTheme.colorScheme.inversePrimary
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = indicator,
                startAngle = 135f,
                sweepAngle = 270f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = stroke,
            )
        }
        Text(
            text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}