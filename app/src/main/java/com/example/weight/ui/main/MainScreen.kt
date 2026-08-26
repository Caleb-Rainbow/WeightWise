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
import com.example.weight.data.record.DailyMinWeight
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

internal const val SCOPE_MENU_ANCHOR_TEST_TAG = "scope_menu_anchor"
internal const val HERO_RING_TEST_TAG = "hero_ring"

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
    quickAddRequest: Boolean = false,
    onQuickAddConsumed: () -> Unit = {},
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

    // 全局导航坞「饮食」长按：回首页并弹快速记一餐
    LaunchedEffect(quickAddRequest) {
        if (quickAddRequest) {
            showQuickAdd = true
            onQuickAddConsumed()
        }
    }
    val height by LocalStorageData.height.collectAsStateWithLifecycle()
    // 普通 remember 计算值即可：此处从不写入，mutableStateOf 是无意义的包装
    val bmi = remember(uiState.selectedRecord, height) {
        val heightMeters = height / 100
        uiState.selectedRecord?.minWeight?.div(heightMeters.times(heightMeters)) ?: 0.0
    }
    MainDialog()
    // 导航坞已上提为全局层(T-4):由 MainActivity 宿主 Scaffold 承载,所有一级目的地常驻
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { paddingValues ->
        // 缓存在 ViewModel 的 StateFlow 里：导航返回直接回放旧值，null 仅表示真正的首次加载
        val currentScopeData by viewModel.currentScopeData.collectAsStateWithLifecycle()
        val scopeData = currentScopeData
        LaunchedEffect(currentScopeData) {
            currentScopeData?.let { viewModel.setSelectedRecord(it.lastOrNull()) }
        }

        Box(modifier = Modifier.fillMaxSize()) {
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
                    val predictionDataList by viewModel.predictionData.collectAsStateWithLifecycle()
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

                    BoxWithConstraints {
                        // 提升到此处一次性收集，避免在下层参数表达式里内联 collect
                        val selectedScope by viewModel.selectedScope.collectAsStateWithLifecycle()
                        // 稳定引用：内联 lambda 每次重组都是新实例，会让图表的 marker listener 链失效重建
                        val onRecordSelected = remember(viewModel) {
                            { record: DailyMinWeight -> viewModel.setSelectedRecord(record) }
                        }

                        val heroContent: @Composable (Modifier) -> Unit = { heroModifier ->
                            Surface(
                                modifier = heroModifier.fillMaxWidth(),
                                shape = RoundedCornerShape(bottomStart = 42.dp, bottomEnd = 14.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Column {
                                    SelectedRecordContent(
                                        record = uiState.selectedRecord,
                                        selectedScope = selectedScope,
                                        onScopeSelected = viewModel::selectScope,
                                        streakInfo = streakInfo,
                                        onSetting = goSetting,
                                    )
                                    GoalProgressContent(
                                        modifier = Modifier
                                            .padding(start = 20.dp, end = 20.dp, bottom = 22.dp)
                                            .fillMaxWidth(),
                                        currentRecord = uiState.selectedRecord,
                                        firstRecord = uiState.firstRecord,
                                        recentDailyWeights = predictionDataList,
                                    )
                                }
                            }
                        }

                        val trendContent: @Composable () -> Unit = {
                            DashboardSectionTitle(
                                index = "01",
                                title = "趋势轨迹",
                                subtitle = "点按曲线查看当天记录",
                                modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(
                                    topStart = 8.dp,
                                    topEnd = 30.dp,
                                    bottomStart = 30.dp,
                                    bottomEnd = 8.dp,
                                ),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shadowElevation = 1.dp,
                            ) {
                                StatisticChart(
                                    currentScopeDataList = scopeData,
                                    maxWeight = chartMaxWeight,
                                    minWeight = chartMinWeight,
                                    onMarkerClick = onRecordSelected,
                                )
                            }
                        }

                        val statsContent: @Composable () -> Unit = {
                            PeriodDigest(
                                modifier = Modifier
                                    .padding(top = 14.dp)
                                    .fillMaxWidth(),
                                maxWeightRecord = maxWeightRecord,
                                minWeightRecord = minWeightRecord,
                                firstWeightRecord = scopeData.firstOrNull(),
                                lastWeightRecord = scopeData.lastOrNull(),
                            )
                        }

                        val bmiContent: @Composable () -> Unit = {
                            DashboardSectionTitle(
                                index = "02",
                                title = "身体状态",
                                subtitle = "BMI 仅作为成年人健康范围参考",
                                modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
                            )
                            BMIContent(
                                modifier = Modifier,
                                record = uiState.selectedRecord,
                                bmi = bmi,
                            )
                        }

                        if (maxWidth >= 700.dp) {
                            Row(
                                modifier = Modifier.padding(horizontal = WeightWiseDimens.PageHorizontal),
                                horizontalArrangement = Arrangement.spacedBy(WeightWiseDimens.SectionGap),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(0.42f)) {
                                    heroContent(Modifier.clip(MaterialTheme.shapes.extraLarge))
                                    DashboardQuickActions(
                                        modifier = Modifier.padding(top = 16.dp),
                                        onAddRecord = viewModel::showAddDialog,
                                        onDietQuickAdd = { showQuickAdd = true },
                                    )
                                    statsContent()
                                }
                                Column(modifier = Modifier.weight(0.58f)) {
                                    trendContent()
                                    bmiContent()
                                }
                            }
                        } else {
                            Column {
                                heroContent(Modifier)
                                Column(modifier = Modifier.padding(horizontal = WeightWiseDimens.PageHorizontal)) {
                                    DashboardQuickActions(
                                        modifier = Modifier.padding(top = 18.dp),
                                        onAddRecord = viewModel::showAddDialog,
                                        onDietQuickAdd = { showQuickAdd = true },
                                    )
                                    trendContent()
                                    statsContent()
                                    bmiContent()
                                }
                            }
                        }
                    }
                }
            }
                Spacer(modifier = Modifier.height(16.dp))
            }
            // 最后绘制为固定承托层：列表滚动后也不会让浅色卡片跑到状态栏图标下方。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.primary),
            )
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

/** 首页的两条高频任务。用不对称比例强调称重，避免所有入口等权的图标宫格。 */
@Composable
private fun DashboardQuickActions(
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
private fun DashboardSectionTitle(
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
                label = "目标进度",
            )
            // 基于近 90 天每日最低体重的加权回归趋势估算剩余天数，
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
private fun GoalRing(progress: Float, modifier: Modifier = Modifier) {
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

@Composable
fun SelectedRecordContent(
    record: DailyMinWeight?,
    selectedScope: StatisticsScope,
    onScopeSelected: (StatisticsScope) -> Unit,
    modifier: Modifier = Modifier,
    streakInfo: StreakInfo = StreakInfo(0, 0, false),
    onSetting: () -> Unit = {},
    statusBarInsets: WindowInsets = WindowInsets.statusBars,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary),
    ) {
        val ringColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(statusBarInsets),
        ) {
            Canvas(
                modifier = Modifier
                    .size(190.dp)
                    .testTag(HERO_RING_TEST_TAG),
            ) {
                drawCircle(color = ringColor, radius = size.minDimension * 0.42f)
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension * 0.29f,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        Column(
            modifier = Modifier
                .windowInsetsPadding(statusBarInsets)
                .padding(start = 24.dp, top = 20.dp, end = 20.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WEIGHTWISE / 今日",
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.1.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.64f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "今日体重",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    IconButton(onClick = onSetting) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.Bottom) {
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
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        letterSpacing = (-2).sp,
                    )
                }
                Text(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .padding(bottom = 10.dp, start = 4.dp),
                    text = "kg",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.64f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(visible = record != null) {
                    record?.timestamp?.let {
                        Text(
                            text = "最近记录  ${TimeUtils.convertMillisToTime(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.66f),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // 至少连续 2 天才展示徽章，单天打卡没有激励意义
                if (streakInfo.currentStreak >= 2) {
                    HeroPill(text = "连续 ${streakInfo.currentStreak} 天")
                }
            }
            Spacer(Modifier.height(14.dp))
            ScopeSelector(
                selected = selectedScope,
                onSelected = onScopeSelected,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun HeroPill(text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.inversePrimary,
        )
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** 轻量化的统计范围选择器：当前范围文字 + 下拉箭头，不与左侧体重数抢视觉 */
@Composable
private fun ScopeSelector(
    selected: StatisticsScope,
    onSelected: (StatisticsScope) -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(contentColor.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClickLabel = "选择统计范围") { expanded = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "观察周期",
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.62f),
        )
        Spacer(Modifier.weight(1f))
        // 菜单只以右侧当前值为锚点；外层整行仍保持 48dp 以上的点击区域。
        Box(modifier = Modifier.testTag(SCOPE_MENU_ANCHOR_TEST_TAG)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = contentColor,
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

/** 周期摘要采用深色横向刊头，不再是三个等权小指标卡。 */
@Composable
private fun PeriodDigest(
    modifier: Modifier,
    maxWeightRecord: DailyMinWeight?,
    minWeightRecord: DailyMinWeight?,
    firstWeightRecord: DailyMinWeight?,
    lastWeightRecord: DailyMinWeight?
) {
    val delta = lastWeightRecord?.minWeight?.minus(firstWeightRecord?.minWeight ?: 0.0) ?: 0.0
    val deltaFormat = remember { DecimalFormat("+#.#;-#.#") }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 28.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1.25f)) {
                Text(
                    "周期变化",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.58f),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        deltaFormat.format(delta),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (delta <= 0) MaterialTheme.colorScheme.inversePrimary
                        else MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        "kg",
                        modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                    )
                }
                Text(
                    buildString {
                        firstWeightRecord?.timestamp?.let { append(TimeUtils.convertMillisToDate(it).takeLast(5)) }
                        append(" → ")
                        lastWeightRecord?.timestamp?.let { append(TimeUtils.convertMillisToDate(it).takeLast(5)) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.52f),
                )
            }
            VerticalDivider(
                Modifier
                    .height(76.dp)
                    .padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.14f),
            )
            Column(modifier = Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DigestExtrema("最高", maxWeightRecord)
                DigestExtrema("最低", minWeightRecord)
            }
        }
    }
}

@Composable
private fun DigestExtrema(
    label: String,
    record: DailyMinWeight?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = String.format(Locale.CHINA, "%.1f", record?.minWeight ?: 0.0),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
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
