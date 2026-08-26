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
        uiState.selectedRecord?.value?.div(heightMeters.times(heightMeters)) ?: 0.0
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
                    val maxWeightRecord = remember(scopeData) { scopeData.maxByOrNull { it.value } }
                    val minWeightRecord = remember(scopeData) { scopeData.minByOrNull { it.value } }
                    val predictionDataList by viewModel.predictionData.collectAsStateWithLifecycle()
                    val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
                    // Y 轴范围并入目标体重，保证目标参考虚线始终可见
                    val chartMaxWeight = remember(maxWeightRecord, targetWeight) {
                        val raw = maxWeightRecord?.value ?: 0.0
                        (if (targetWeight > 0) maxOf(raw, targetWeight) else raw).plus(1)
                    }
                    val chartMinWeight = remember(minWeightRecord, targetWeight) {
                        val raw = minWeightRecord?.value ?: 0.0
                        (if (targetWeight > 0) minOf(raw, targetWeight) else raw).minus(1)
                    }

                    BoxWithConstraints {
                        // 提升到此处一次性收集，避免在下层参数表达式里内联 collect
                        val selectedScope by viewModel.selectedScope.collectAsStateWithLifecycle()
                        // 稳定引用：内联 lambda 每次重组都是新实例，会让图表的 marker listener 链失效重建
                        val onRecordSelected = remember(viewModel) {
                            { record: DailyWeight -> viewModel.setSelectedRecord(record) }
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
                                        currentRecord = uiState.latestRecord,
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
                                    selectedScope = selectedScope,
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
