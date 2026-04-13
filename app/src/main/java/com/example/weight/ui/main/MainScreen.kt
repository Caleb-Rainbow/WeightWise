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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush.Companion.verticalGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalShowMessageDialog
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.data.record.Record
import com.example.weight.ui.common.BottomXDateFormatter
import com.example.weight.ui.common.ExposedOutlineTextFieldGenericListDropdownMenu
import com.example.weight.ui.common.rememberMarker
import com.example.weight.ui.theme.MyIconPack
import com.example.weight.ui.theme.myiconpack.CalendarCheck
import com.example.weight.util.TimeUtils
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
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.vicoTheme
import org.koin.androidx.compose.koinViewModel
import java.text.DecimalFormat
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier, viewModel: MainViewModel = koinViewModel(),goSetting:()-> Unit = {},goRecord:()-> Unit={},goExercisePlan:()->Unit={},goJourneyCreation:()->Unit={},goJourneyProgress:()->Unit={},goDietRecord:()->Unit={}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    val showMessageDialog = LocalShowMessageDialog.current
    MainDialog()
    AnalysisBottomSheet(
        isLoading = dialogState.isLoading,
        showSheet = dialogState.isShowAiAnalyzeBottomSheet,
        analysisResult = uiState.analyzeResponse,
        onDismissRequest = viewModel::hideAiAnalyzeBottomSheet
    )
    val height by LocalStorageData.height.collectAsStateWithLifecycle()
    val bmi by remember(uiState.selectedRecord,height) {
        val height = height / 100
        mutableDoubleStateOf(uiState.selectedRecord?.minWeight?.div(height.times(height)) ?: 0.0)
    }
    Scaffold(
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                val currentScopeDataList by viewModel.currentScopeData.collectAsStateWithLifecycle(initialValue = emptyList())
                LaunchedEffect(currentScopeDataList) {
                    viewModel.setSelectedRecord(currentScopeDataList.lastOrNull())
                }
                val maxWeightRecord = remember(currentScopeDataList) { currentScopeDataList.maxByOrNull { it.minWeight } }
                val minWeightRecord = remember(currentScopeDataList) { currentScopeDataList.minByOrNull { it.minWeight } }

                SelectedRecordContent(record = uiState.selectedRecord)
                GoalProgressContent(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .padding(top = 15.dp)
                        .fillMaxWidth(),
                    currentRecord = uiState.selectedRecord,
                    firstRecord = uiState.firstRecord
                )
                StatisticChart(
                    currentScopeDataList = currentScopeDataList,
                    maxWeight = maxWeightRecord?.minWeight?.plus(1) ?: 0.0,
                    minWeight = minWeightRecord?.minWeight?.minus(1) ?: 0.0
                ) {
                    viewModel.setSelectedRecord(it)
                }
                MinAndMaxDataContent(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .padding(top = 20.dp),
                    maxWeightRecord = maxWeightRecord,
                    minWeightRecord = minWeightRecord,

                    )
                BMIContent(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .padding(top = 15.dp), record = uiState.selectedRecord, bmi = bmi
                )
                IndicatorChangesContent(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .padding(top = 15.dp), firstWeightRecord = currentScopeDataList.firstOrNull(),
                    lastWeightRecord = currentScopeDataList.lastOrNull()
                )
                Spacer(modifier = Modifier.height(80.dp))
            }
            HorizontalFloatingToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-10).dp), expanded = true, expandedShadowElevation = 2.dp
            ) {
                IconButton(onClick = goExercisePlan){
                    Icon(imageVector = MyIconPack.CalendarCheck, contentDescription = null)
                }
                val activeJourneyId by com.example.weight.data.LocalStorageData.activeJourneyId.collectAsStateWithLifecycle()
                IconButton(onClick = {
                    if (activeJourneyId > 0) {
                        goJourneyProgress()
                    } else {
                        goJourneyCreation()
                    }
                }){
                    Icon(imageVector = Icons.Default.Flag, contentDescription = null)
                }
                IconButton(onClick = goSetting) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                }
                FilledIconButton(onClick = viewModel::showAddDialog) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                }
                IconButton(onClick = goDietRecord) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null)
                }
                IconButton(onClick = goRecord) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null
                    )
                }
                IconButton(onClick = {
                    viewModel.aiAnalyze(bmi = bmi) {
                        showMessageDialog("提示", it) {}
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoalProgressContent(
    modifier: Modifier,
    currentRecord: DailyMinWeight?,
    firstRecord: Record?
) {
    currentRecord?.let {
        val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
        val startWeight = firstRecord?.weight ?: 0.0
        val currentWeight = it.minWeight

        // 核心逻辑：计算从起始到目标的进度百分比
        val progress = remember(startWeight, currentWeight, targetWeight) {
            val totalRange = startWeight - targetWeight
            // 避免起始体重和目标体重相同导致的除零错误
            if (totalRange.compareTo(0.0) == 0) {
                return@remember if (currentWeight <= targetWeight) 1.0f else 0.0f
            }
            val traveled = startWeight - currentWeight
            // 将进度限制在0.0到1.0之间，以正确显示进度条
            (traveled / totalRange).toFloat().coerceIn(0.0f, 1.0f)
        }
        // 使用 animateFloatAsState 为进度条增加平滑的动画效果
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
        )

        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // "距离目标还有 X kg，预计剩余 N 天" 的核心激励文本
                val remainingWeight = (currentWeight - targetWeight).absoluteValue
                // 基于历史减重速率估算剩余天数
                val remainingDays = remember(startWeight, currentWeight, targetWeight, firstRecord) {
                    val elapsedMillis = System.currentTimeMillis() - (firstRecord?.timestamp ?: 0L)
                    val elapsedDays = elapsedMillis / (1000.0 * 60 * 60 * 24)
                    val weightLost = startWeight - currentWeight
                    // 已减重需为正且经过天数>=1才能估算
                    if (weightLost > 0 && elapsedDays >= 1) {
                        val dailyRate = weightLost / elapsedDays
                        val daysLeft = remainingWeight / dailyRate
                        daysLeft.toLong().coerceAtLeast(0)
                    } else {
                        null // 数据不足时不显示天数
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        append("距离目标还有 ")
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            append(String.format(java.util.Locale.CHINA, "%.1f", remainingWeight))
                        }
                        append(" kg")
                        // 仅在能估算出天数时显示
                        if (remainingDays != null && remainingWeight > 0) {
                            withStyle(
                                style = SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                append(" ${remainingDays}")
                            }
                            append(" 天")
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 进度条本体
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth(),
                    trackColor = Color(0xFFDBF0FF)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 进度条下方的 "起始" 和 "目标" 重量标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "起始: ${
                            String.format(
                                java.util.Locale.CHINA,
                                "%.1f",
                                startWeight
                            )
                        } kg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "目标: ${
                            String.format(
                                java.util.Locale.CHINA,
                                "%.1f",
                                targetWeight
                            )
                        } kg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SelectedRecordContent(record: DailyMinWeight?) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp),
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
                    Text(text = it.toString(), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
                Text(modifier = Modifier.align(Alignment.Bottom), text = "kg", fontSize = 18.sp)
            }
            AnimatedVisibility(visible = record != null) {
                record?.timestamp?.let {
                    Text(
                        text = TimeUtils.convertMillisToTime(it),
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
        StatisticsScopeContent(modifier = Modifier.width(150.dp))
    }
}

@Composable
private fun StatisticsScopeContent(modifier: Modifier, viewModel: MainViewModel = koinViewModel()) {
    val selectedScope by viewModel.selectedScope.collectAsStateWithLifecycle()
    val statisticsScope = remember { StatisticsScope.entries.toList() }
    ExposedOutlineTextFieldGenericListDropdownMenu(
        modifier = modifier,
        hint = "统计范围",
        initialDescription = selectedScope.label,
        descriptionList = statisticsScope.map { it.label },
        genericList = statisticsScope,
        onSelected = {
            viewModel.selectScope(it)
        })
}

@Composable
private fun StatisticChart(
    currentScopeDataList: List<DailyMinWeight>,
    maxWeight: Double,
    minWeight: Double,
    onMarkerClick: (DailyMinWeight) -> Unit
) {
    // 根据收集到的数据构建 LineChart 所需的参数，当 currentScopeDataList 变化时重组
    val labels = remember(currentScopeDataList) { currentScopeDataList.map { it.recordDay } }


    // 当 chartData 不为空时才显示图表
    if (currentScopeDataList.isNotEmpty()) {
        key(currentScopeDataList) {
            val modelProducer = remember { CartesianChartModelProducer() }
            LaunchedEffect(Unit) {
                modelProducer.runTransaction {
                    lineSeries { series(currentScopeDataList.map { it.minWeight }) }
                }
            }
            WeightChart(
                lineColor = vicoTheme.lineColor,
                modelProducer = modelProducer,
                maxWeight = maxWeight,
                minWeight = minWeight,
                xLabels = labels
            ) {
                onMarkerClick(currentScopeDataList[it])
            }
        }
    }
}

@Composable
private fun WeightChart(
    modelProducer: CartesianChartModelProducer,
    modifier: Modifier = Modifier,
    xLabels: List<String> = emptyList(),
    maxWeight: Double,
    minWeight: Double,
    lineColor: Color,
    onMarkerClick: (Int) -> Unit = {}
) {
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
                        )
                    ),
                rangeProvider = CartesianLayerRangeProvider.fixed(maxY = maxWeight, minY = minWeight),
            ),
            startAxis = VerticalAxis.rememberStart(
                title = {"体重"},
                valueFormatter = CartesianValueFormatter.decimal(decimalCount = 2, suffix = "kg"),
                itemPlacer = remember { VerticalAxis.ItemPlacer.step(step = { 0.5 }) }),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = BottomXDateFormatter(labels = xLabels)),
            marker = rememberMarker(valueFormatter = remember {
                DefaultCartesianMarker.ValueFormatter.default(decimalCount = 2, suffix = "kg")
            }),
            markerVisibilityListener = object : CartesianMarkerVisibilityListener {
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
        ),
        modelProducer = modelProducer,
        modifier = modifier.height(220.dp),
        scrollState = rememberVicoScrollState(scrollEnabled = true, initialScroll = Scroll.Absolute.End),
    )
}

@Composable
private fun MinAndMaxDataContent(
    modifier: Modifier,
    maxWeightRecord: DailyMinWeight?,
    minWeightRecord: DailyMinWeight?,
) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            MaxAndMinRecordItem(record = maxWeightRecord, title = "最高体重")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VerticalDivider(Modifier.height(15.dp))
                Text(
                    text = DecimalFormat("#.#kg").format(maxWeightRecord?.minWeight?.minus(minWeightRecord?.minWeight ?: 0.0) ?: 0.0),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 12.sp
                )
                VerticalDivider(Modifier.height(15.dp))
            }
            MaxAndMinRecordItem(record = minWeightRecord, title = "最低体重")
        }
    }
}

@Composable
private fun MaxAndMinRecordItem(record: DailyMinWeight?, title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = buildAnnotatedString {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                append(record?.minWeight?.toString() ?: "0.0")
            }
            append("kg")
        })

        Text(text = title, fontSize = 14.sp)
        AnimatedVisibility(visible = record != null) {
            record?.timestamp?.let {
                Text(
                    text = TimeUtils.convertMillisToDate(it),
                    fontSize = 10.sp,
                    color = Color.Gray,
                    lineHeight = 10.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IndicatorChangesContent(
    modifier: Modifier,
    firstWeightRecord: DailyMinWeight?,
    lastWeightRecord: DailyMinWeight?
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = "指标变化", style = MaterialTheme.typography.titleMediumEmphasized)
            Row {
                Text(
                    text = DecimalFormat("+#.#;-#.#").format(
                        lastWeightRecord?.minWeight?.minus(
                            firstWeightRecord?.minWeight ?: 0.0
                        ) ?: 0.0
                    ),
                    style = MaterialTheme.typography.titleLargeEmphasized
                )
                Text(
                    modifier = Modifier.align(Alignment.Bottom),
                    text = "kg",
                    style = MaterialTheme.typography.bodySmallEmphasized
                )
            }
            Text(
                text = "${TimeUtils.convertMillisToDate(firstWeightRecord?.timestamp ?: 0)} 至 ${
                    TimeUtils.convertMillisToDate(
                        lastWeightRecord?.timestamp ?: 0
                    )
                }",
                style = MaterialTheme.typography.bodySmallEmphasized
            )
        }
    }
}

enum class StatisticsScope(val label: String) {
    LAST_7DAYS("近7天"),
    LAST_14DAYS("近14天"),
    LAST_1MONTH("近1月"),
    LAST_3MONTH("近3月"),
    LAST_6MONTH("近6月"),
    LAST_1YEARS("近1年"),
    LAST_2YEARS("近2年"),
    LAST_3YEARS("近3年"),
}

