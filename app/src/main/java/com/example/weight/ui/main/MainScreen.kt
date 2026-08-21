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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MonitorWeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.data.record.Record
import com.example.weight.ui.common.WeightChart
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = koinViewModel(),
    goSetting: () -> Unit = {},
    goRecord: () -> Unit = {},
    goDietRecord: () -> Unit = {},
    goReport: () -> Unit = {},
    openAddDialogRequest: Boolean = false,
    onOpenAddDialogConsumed: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    val streakInfo by viewModel.streakInfo.collectAsStateWithLifecycle()
    val snackBarShow = LocalSnackBarShow.current

    // 工具栏「饮食」→ 快速记一笔弹层（一级入口）；sheet 内「拍照识别」才进完整饮食页
    var showQuickAdd by remember { mutableStateOf(false) }
    if (showQuickAdd) {
        QuickAddSheet(
            onGoFullDiet = goDietRecord,
            onDismiss = { showQuickAdd = false },
        )
    }

    // 里程碑达成时弹一次庆祝提示
    LaunchedEffect(Unit) {
        viewModel.milestoneCelebration.collect { snackBarShow(it) }
    }

    // 通知/小组件点击的「直达记体重」深链
    LaunchedEffect(openAddDialogRequest) {
        if (openAddDialogRequest) {
            viewModel.showAddDialog()
            onOpenAddDialogConsumed()
        }
    }
    val height by LocalStorageData.height.collectAsStateWithLifecycle()
    // 普通 remember 计算值即可：此处从不写入，mutableStateOf 是无意义的包装
    val bmi = remember(uiState.selectedRecord, height) {
        val heightMeters = height / 100
        uiState.selectedRecord?.minWeight?.div(heightMeters.times(heightMeters)) ?: 0.0
    }
    MainDialog()
    Scaffold(
        modifier = modifier,
        bottomBar = {
            MainBottomToolbar(
                onSetting = goSetting,
                onAddRecord = viewModel::showAddDialog,
                onDietRecord = goDietRecord,
                onDietQuickAdd = { showQuickAdd = true },
                onRecord = goRecord,
                onReport = goReport
            )
        }
    ) { paddingValues ->
        // null 表示范围切换的加载瞬间，用上一次数据兜底，避免内容闪烁
        val currentScopeDataList by viewModel.currentScopeData.collectAsStateWithLifecycle(initialValue = null)
        var cachedData by remember { mutableStateOf<List<DailyMinWeight>?>(null) }
        val scopeData = currentScopeDataList ?: cachedData
        LaunchedEffect(currentScopeDataList) {
            val list = currentScopeDataList
            if (list != null) {
                cachedData = list
                viewModel.setSelectedRecord(list.lastOrNull())
            }
        }

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            when {
                scopeData == null -> LoadingContent()
                scopeData.isEmpty() -> EmptyContent(onAddRecord = viewModel::showAddDialog)
                else -> {
                    val maxWeightRecord = remember(scopeData) { scopeData.maxByOrNull { it.minWeight } }
                    val minWeightRecord = remember(scopeData) { scopeData.minByOrNull { it.minWeight } }
                    val predictionDataList by viewModel.predictionData.collectAsStateWithLifecycle(initialValue = emptyList())
                    val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
                    // Y 轴范围并入目标体重，保证目标参考虚线始终可见
                    val chartMaxWeight = remember(maxWeightRecord, targetWeight) {
                        val raw = maxWeightRecord?.minWeight ?: 0.0
                        (if (targetWeight > 0) maxOf(raw, targetWeight) else raw).plus(1)
                    }
                    val chartMinWeight = remember(minWeightRecord, targetWeight) {
                        val raw = minWeightRecord?.minWeight ?: 0.0
                        (if (targetWeight > 0) minOf(raw, targetWeight) else raw).minus(1)
                    }

                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        // 提升到此处一次性收集，避免在下层参数表达式里内联 collect
                        val selectedScope by viewModel.selectedScope.collectAsStateWithLifecycle()
                        SelectedRecordContent(
                            record = uiState.selectedRecord,
                            selectedScope = selectedScope,
                            onScopeSelected = viewModel::selectScope,
                            streakInfo = streakInfo
                        )
                        GoalProgressContent(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            currentRecord = uiState.selectedRecord,
                            firstRecord = uiState.firstRecord,
                            recentDailyWeights = predictionDataList
                        )
                        // 稳定引用：内联 lambda 每次重组都是新实例，会让图表的 marker listener 链失效重建
                        val onRecordSelected = remember(viewModel) {
                            { record: DailyMinWeight -> viewModel.setSelectedRecord(record) }
                        }
                        StatisticChart(
                            currentScopeDataList = scopeData,
                            maxWeight = chartMaxWeight,
                            minWeight = chartMinWeight,
                            onMarkerClick = onRecordSelected,
                        )
                        StatsSummaryCard(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                            maxWeightRecord = maxWeightRecord,
                            minWeightRecord = minWeightRecord,
                            firstWeightRecord = scopeData.firstOrNull(),
                            lastWeightRecord = scopeData.lastOrNull()
                        )
                        BMIContent(
                            modifier = Modifier.padding(top = 12.dp),
                            record = uiState.selectedRecord,
                            bmi = bmi
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** 首次进入、数据库尚未发出第一份数据时的短暂加载态 */
@Composable
private fun LoadingContent() {
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
private fun EmptyContent(onAddRecord: () -> Unit) {
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

/**
 * 首页底部操作栏:胶囊形悬浮样式,五个入口等宽分布,中心的"记体重"按钮放大突出。
 * 通过 [androidx.compose.material3.Scaffold] 的 bottomBar 槽位使用,自动为内容预留底部内边距。
 */
@Composable
private fun MainBottomToolbar(
    onSetting: () -> Unit,
    onAddRecord: () -> Unit,
    onDietRecord: () -> Unit,
    onDietQuickAdd: () -> Unit,
    onRecord: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainToolbarItem(label = "设置", icon = Icons.Default.Settings, onClick = onSetting)
            // 点击直达饮食页；长按弹快速记一笔 sheet（用户反馈：一级入口不能挡住主路径）
            MainToolbarItem(
                label = "饮食",
                icon = Icons.Default.CameraAlt,
                onClick = onDietRecord,
                onLongClick = onDietQuickAdd,
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FilledIconButton(
                    onClick = onAddRecord,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "记体重",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            MainToolbarItem(
                label = "记录",
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                onClick = onRecord
            )
            MainToolbarItem(
                label = "报告",
                icon = Icons.Default.Insights,
                onClick = onReport
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.MainToolbarItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        role = Role.Button,
                        onClickLabel = label,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                }
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoalProgressContent(
    modifier: Modifier,
    currentRecord: DailyMinWeight?,
    firstRecord: Record?,
    recentDailyWeights: List<DailyMinWeight>
) {
    currentRecord?.let {
        val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
        val configuredStartWeight by LocalStorageData.startWeight.collectAsStateWithLifecycle()
        // 手动设置的起始体重优先，未设置时跟随第一条记录；设置页修改后此处实时重组
        val startWeight =
            GoalProgressCalculator.effectiveStartWeight(configuredStartWeight, firstRecord?.weight)
                ?: 0.0
        val currentWeight = it.minWeight
        if (targetWeight > 0) {
            val goalReached = currentWeight <= targetWeight

            // 核心逻辑：计算从起始到目标的进度百分比（算法与桌面小组件共用）
            val progress = remember(startWeight, currentWeight, targetWeight) {
                GoalProgressCalculator.progress(startWeight, currentWeight, targetWeight)
            }
            // 使用 animateFloatAsState 为进度条增加平滑的动画效果
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
            )
            // 基于近 90 天每日最低体重的加权回归趋势估算剩余天数，
            // 趋势停滞、反向或数据不足时返回 null，不显示天数
            val remainingDays = remember(recentDailyWeights, currentWeight, targetWeight) {
                WeightPredictor.estimateDaysToTarget(recentDailyWeights, currentWeight, targetWeight)
            }

            Card(
                modifier = modifier,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    if (goalReached) {
                        // 达成目标的庆祝态
                        Text(
                            text = "已达成目标体重",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val lostWeight = startWeight - currentWeight
                        Text(
                            text = if (lostWeight > 0) {
                                "相比起始体重已减轻 ${String.format(Locale.CHINA, "%.1f", lostWeight)} kg，继续保持"
                            } else {
                                "继续保持，稳住成果"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        // "距离目标还有 X kg，预计剩余 N 天" 的核心激励文本
                        val remainingWeight = (currentWeight - targetWeight).absoluteValue
                        Text(
                            text = buildAnnotatedString {
                                append("距离目标还有 ")
                                withStyle(
                                    style = SpanStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                ) {
                                    append(String.format(Locale.CHINA, "%.1f", remainingWeight))
                                }
                                append(" kg")
                                // 仅在能估算出天数时显示
                                if (remainingDays != null && remainingWeight > 0) {
                                    append("，预计 ")
                                    withStyle(
                                        style = SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    ) {
                                        append(" $remainingDays")
                                    }
                                    append(" 天")
                                }
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 进度条本体
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 进度条下方的 "起始" 和 "目标" 重量标签
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "起始 ${String.format(Locale.CHINA, "%.1f", startWeight)} kg",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "目标 ${String.format(Locale.CHINA, "%.1f", targetWeight)} kg",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedRecordContent(
    record: DailyMinWeight?,
    selectedScope: StatisticsScope,
    onScopeSelected: (StatisticsScope) -> Unit,
    streakInfo: StreakInfo = StreakInfo(0, 0, false)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row {
                AnimatedContent(targetState = record?.minWeight ?: 0.0, transitionSpec = {
                    if (targetState > initialState) {
                        slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                    } else {
                        slideInVertically { height -> -height } + fadeIn() togetherWith
                            slideOutVertically { height -> height } + fadeOut()
                    }.using(
                        SizeTransform(clip = false)
                    )
                }) {
                    Text(
                        text = String.format(Locale.CHINA, "%.1f", it),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .padding(bottom = 3.dp),
                    text = "kg",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(visible = record != null) {
                    record?.timestamp?.let {
                        Text(
                            text = TimeUtils.convertMillisToTime(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 至少连续 2 天才展示徽章，单天打卡没有激励意义
                if (streakInfo.currentStreak >= 2) {
                    if (record != null) Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "🔥 连续 ${streakInfo.currentStreak} 天",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
        ScopeSelector(selected = selectedScope, onSelected = onScopeSelected)
    }
}

/** 轻量化的统计范围选择器：当前范围文字 + 下拉箭头，不与左侧体重数抢视觉 */
@Composable
private fun ScopeSelector(
    selected: StatisticsScope,
    onSelected: (StatisticsScope) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClickLabel = "选择统计范围") { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selected.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StatisticsScope.entries.forEach { scope ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = scope.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (scope == selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelected(scope)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StatisticChart(
    currentScopeDataList: List<DailyMinWeight>,
    maxWeight: Double,
    minWeight: Double,
    onMarkerClick: (DailyMinWeight) -> Unit,
) {
    // 根据收集到的数据构建 LineChart 所需的参数，当 currentScopeDataList 变化时重组
    val labels = remember(currentScopeDataList) { currentScopeDataList.map { it.recordDay } }
    // 7 日移动平均：对记录序列做 7 点滑动窗口平均，与图表按记录排布的横轴自洽；
    // 数据不足 7 条时（如近7天范围内）均线无意义，不画
    val movingAverage = remember(currentScopeDataList) { movingAverage(currentScopeDataList.map { it.minWeight }) }
    val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()

    // 当 chartData 不为空时才显示图表
    if (currentScopeDataList.isNotEmpty()) {
        // producer 保持稳定，数据变化只增量提交事务；不销毁整棵图表子树（含滚动状态）
        val modelProducer = remember { CartesianChartModelProducer() }
        LaunchedEffect(currentScopeDataList, movingAverage) {
            modelProducer.runTransaction {
                lineModel {
                    series(currentScopeDataList.map { it.minWeight })
                    if (movingAverage.isNotEmpty()) {
                        // 均线从第 7 个记录点起才有完整窗口，用显式 x 对齐横轴
                        series(
                            x = currentScopeDataList.indices.drop(6),
                            y = movingAverage,
                        )
                    }
                }
            }
        }
        // 稳定的索引回调：内联 lambda 每次重组生成新实例，会顺着 WeightChart 的
        // remember 链触发 marker listener 重建乃至整张图表重建
        val onMarkerIndexClick = remember(onMarkerClick, currentScopeDataList) {
            { index: Int -> onMarkerClick(currentScopeDataList[index]) }
        }
        WeightChart(
            lineColor = vicoTheme.lineColor,
            modelProducer = modelProducer,
            maxWeight = maxWeight,
            minWeight = minWeight,
            xLabels = labels,
            showMovingAverage = movingAverage.isNotEmpty(),
            targetWeight = targetWeight,
            onMarkerClick = onMarkerIndexClick,
        )
    }
}

/**
 * 范围内统计汇总卡：最高 / 最低 / 变化 三列合一。
 * 原来的"最高最低体重"卡与"指标变化"卡职责相同（范围内汇总），合并后每卡一个职责。
 */
@Composable
private fun StatsSummaryCard(
    modifier: Modifier,
    maxWeightRecord: DailyMinWeight?,
    minWeightRecord: DailyMinWeight?,
    firstWeightRecord: DailyMinWeight?,
    lastWeightRecord: DailyMinWeight?
) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                modifier = Modifier.weight(1f),
                value = String.format(Locale.CHINA, "%.1f", maxWeightRecord?.minWeight ?: 0.0),
                label = "最高",
                sub = maxWeightRecord?.timestamp?.let { TimeUtils.convertMillisToDate(it) }
            )
            VerticalDivider(Modifier.height(40.dp))
            StatItem(
                modifier = Modifier.weight(1f),
                value = String.format(Locale.CHINA, "%.1f", minWeightRecord?.minWeight ?: 0.0),
                label = "最低",
                sub = minWeightRecord?.timestamp?.let { TimeUtils.convertMillisToDate(it) }
            )
            VerticalDivider(Modifier.height(40.dp))
            val delta = lastWeightRecord?.minWeight?.minus(firstWeightRecord?.minWeight ?: 0.0) ?: 0.0
            StatItem(
                modifier = Modifier.weight(1f),
                value = DecimalFormat("+#.#;-#.#").format(delta),
                label = "变化",
                sub = buildString {
                    firstWeightRecord?.timestamp?.let { append(TimeUtils.convertMillisToDate(it).takeLast(5)) }
                    append(" 至 ")
                    lastWeightRecord?.timestamp?.let { append(TimeUtils.convertMillisToDate(it).takeLast(5)) }
                },
                valueColor = if (delta <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun StatItem(
    modifier: Modifier,
    value: String,
    label: String,
    sub: String?,
    valueColor: Color = Color.Unspecified
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (sub != null) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

enum class StatisticsScope(val label: String) {
    LAST_7DAYS("近7天"),
    LAST_14DAYS("近14天"),
    LAST_1MONTH("近1月"),
    LAST_3MONTHS("近3月"),
    LAST_6MONTHS("近6月"),
    LAST_1YEARS("近1年"),
    LAST_2YEARS("近2年"),
    LAST_3YEARS("近3年");

    /**
     * 该统计范围的起始时间戳（范围第一天的午夜）。
     * 图表取数与 AI 分析取数共用这一映射，保证分析的数据范围与界面所选范围一致
     */
    fun startTimeMillis(): Long = when (this) {
        LAST_7DAYS -> TimeUtils.getStartTimeForLastDays(7)
        LAST_14DAYS -> TimeUtils.getStartTimeForLastDays(14)
        LAST_1MONTH -> TimeUtils.getStartTimeForLastMonths(1)
        LAST_3MONTHS -> TimeUtils.getStartTimeForLastMonths(3)
        LAST_6MONTHS -> TimeUtils.getStartTimeForLastMonths(6)
        LAST_1YEARS -> TimeUtils.getStartTimeForLastMonths(12)
        LAST_2YEARS -> TimeUtils.getStartTimeForLastMonths(24)
        LAST_3YEARS -> TimeUtils.getStartTimeForLastMonths(36)
    }
}
