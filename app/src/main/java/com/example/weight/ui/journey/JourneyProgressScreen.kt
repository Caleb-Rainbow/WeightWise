package com.example.weight.ui.journey

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.exercise.Phase
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.ui.common.BottomXDateFormatter
import com.example.weight.ui.common.MyTopBar
import com.example.weight.ui.common.rememberMarker
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.vicoTheme
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyProgressScreen(
    goBack: () -> Unit,
    goExercisePlan: () -> Unit,
    goJourneyCreation: () -> Unit = {},
    viewModel: JourneyProgressViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAbandonDialog by remember { mutableStateOf(false) }

    if (state.isJourneyNotFound) {
        Scaffold(
            topBar = { MyTopBar(title = "旅程进度", goBack = goBack) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "暂无进行中的旅程",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(onClick = goJourneyCreation) {
                    Text("开启新旅程")
                }
            }
        }
        return
    }

    // 提前达标弹窗 + Confetti
    if (state.earlyCompleted) {
        Box(modifier = Modifier.fillMaxSize()) {
            ConfettiOverlay()
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(
                        "恭喜提前达成目标！",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                text = { Text("你已经达到了目标体重，是否结束当前旅程？") },
                confirmButton = {
                    TextButton(onClick = { viewModel.completeJourney() }) {
                        Text("结束旅程")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissEarlyCompletion() }) { Text("继续旅程") }
                },
            )
        }
    }

    // 放弃确认弹窗
    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { showAbandonDialog = false },
            title = { Text("放弃旅程？") },
            text = { Text("放弃后进度将保留，但旅程将不再追踪。确定要放弃吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAbandonDialog = false
                        viewModel.abandonJourney()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonDialog = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = { MyTopBar(title = "旅程进度", goBack = goBack) }
    ) { paddingValues ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val journey = state.journey ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 总进度卡片
            OverallProgressCard(
                currentDay = state.currentDay,
                totalDays = state.totalDays,
                startWeight = journey.startWeight,
                targetWeight = journey.targetWeight,
                weightLost = state.weightProgress.lastOrNull()?.let { journey.startWeight - it.minWeight } ?: 0.0,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 体重趋势图
            if (state.weightProgress.isNotEmpty()) {
                WeightTrendChart(weightProgress = state.weightProgress)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 完成统计卡片
            StatsCard(
                completedExercises = state.completedExercises,
                totalExercises = state.totalExercises,
                completionRate = state.completionRate,
                streakDays = state.streakDays,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 阶段时间轴
            PhaseTimeline(
                phases = state.phases,
                currentDay = state.currentDay,
                currentPhaseId = state.currentPhase?.id,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = goExercisePlan,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("前往今日计划")
                }
                OutlinedButton(
                    onClick = { showAbandonDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("放弃旅程")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OverallProgressCard(
    currentDay: Int,
    totalDays: Int,
    startWeight: Double,
    targetWeight: Double,
    weightLost: Double,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "第${currentDay}天 / 共${totalDays}天",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (currentDay.toFloat() / totalDays).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem(label = "起始", value = "${"%.1f".format(startWeight)}kg")
                StatItem(label = "目标", value = "${"%.1f".format(targetWeight)}kg")
                StatItem(label = "已减", value = "${"%.1f".format(weightLost.coerceAtLeast(0.0))}kg")
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatsCard(
    completedExercises: Int,
    totalExercises: Int,
    completionRate: Float,
    streakDays: Int,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(label = "完成运动", value = "$completedExercises")
            StatItem(label = "总运动数", value = "$totalExercises")
            StatItem(label = "完成率", value = "${"%.0f".format(completionRate * 100)}%")
            StatItem(label = "连续打卡", value = "${streakDays}天")
        }
    }
}

@Composable
private fun WeightTrendChart(weightProgress: List<DailyMinWeight>) {
    val labels = remember(weightProgress) { weightProgress.map { it.recordDay } }
    val maxWeight = weightProgress.maxOfOrNull { it.minWeight }?.plus(1) ?: 0.0
    val minWeight = weightProgress.minOfOrNull { it.minWeight }?.minus(1) ?: 0.0

    key(weightProgress) {
        val modelProducer = remember { CartesianChartModelProducer() }
        val lineColor = vicoTheme.lineColor
        LaunchedEffect(Unit) {
            modelProducer.runTransaction {
                lineSeries { series(weightProgress.map { it.minWeight }) }
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "体重趋势",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CartesianChartHost(
                    rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
                                    areaFill = LineCartesianLayer.AreaFill.single(
                                        Fill(
                                            androidx.compose.ui.graphics.Brush.Companion.verticalGradient(
                                                listOf(lineColor.copy(alpha = 0.4f), Color.Transparent)
                                            )
                                        )
                                    ),
                                )
                            ),
                            rangeProvider = CartesianLayerRangeProvider.fixed(maxY = maxWeight, minY = minWeight),
                        ),
                        startAxis = VerticalAxis.rememberStart(
                            title = { "体重" },
                            valueFormatter = CartesianValueFormatter.decimal(decimalCount = 1, suffix = "kg"),
                            itemPlacer = remember { VerticalAxis.ItemPlacer.step(step = { 0.5 }) },
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = BottomXDateFormatter(labels = labels),
                        ),
                        marker = rememberMarker(
                            valueFormatter = remember {
                                com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker.ValueFormatter.default(
                                    decimalCount = 1,
                                    suffix = "kg"
                                )
                            },
                        ),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    scrollState = rememberVicoScrollState(
                        scrollEnabled = true,
                        initialScroll = Scroll.Absolute.End,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PhaseTimeline(
    phases: List<Phase>,
    currentDay: Int,
    currentPhaseId: Int?,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "阶段规划",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            phases.forEachIndexed { index, phase ->
                val isCompleted = currentDay > phase.endDay
                val isCurrent = phase.id == currentPhaseId

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 阶段指示器
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(40.dp),
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        } else if (isCurrent) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(28.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = phase.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isCompleted -> MaterialTheme.colorScheme.primary
                                isCurrent -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = "第${phase.startDay}-${phase.endDay}天 · 目标减重${"%.1f".format(phase.targetWeightLoss)}kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = phase.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }

                    if (isCurrent) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ) {
                            Text(
                                text = "当前",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                if (index < phases.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// ==================== Confetti 动画 ====================

private data class ConfettiParticle(
    val color: Color,
    val startX: Float,
    val startY: Float,
    val speed: Float,
    val angle: Float,
    val size: Float,
    val rotationSpeed: Float,
)

@Composable
private fun ConfettiOverlay(durationMs: Int = 3000) {
    val particles = remember {
        val colors = listOf(
            Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0),
            Color(0xFF3F51B5), Color(0xFF03A9F4), Color(0xFF009688),
            Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFF4CAF50),
        )
        List(60) {
            ConfettiParticle(
                color = colors.random(),
                startX = Random.nextFloat(),
                startY = -Random.nextFloat() * 0.3f,
                speed = Random.nextFloat() * 0.4f + 0.3f,
                angle = Random.nextFloat() * 360f,
                size = Random.nextFloat() * 6f + 4f,
                rotationSpeed = Random.nextFloat() * 360f + 180f,
            )
        }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMs),
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - progress.value }
    ) {
        val t = progress.value
        particles.forEach { p ->
            val x = p.startX * size.width + cos(Math.toRadians(p.angle.toDouble())).toFloat() * t * 50f
            val y = (p.startY + t * p.speed) * size.height
            val rotation = t * p.rotationSpeed
            val currentSize = p.size * (1f - t * 0.5f)

            drawCircle(
                color = p.color,
                radius = currentSize,
                center = Offset(x, y),
                alpha = 1f - t,
            )
        }
    }
}
