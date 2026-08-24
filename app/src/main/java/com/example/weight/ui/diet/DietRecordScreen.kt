package com.example.weight.ui.diet

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.LocalSnackbarHostState
import com.example.weight.data.diet.DailyMacros
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.diet.DailyMacroAggregator
import com.example.weight.data.diet.Macros
import com.example.weight.data.diet.MealType
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.ui.common.MyTopBar
import com.example.weight.ui.common.WeightWiseDimens
import com.example.weight.ui.common.WeightWiseEmptyState
import com.example.weight.ui.theme.resolve
import com.example.weight.util.CalorieCalculator
import com.example.weight.util.IntakeStatus
import com.example.weight.util.ImageCompressor
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 *@description: 饮食记录主界面(v1.6 预算制重构)。
 *               今日=剩余额度大数字+状态圆环+宏量堆叠条+餐次分组 B 行;
 *               新增=独立任务页 + 情境化主按钮(保存这餐/开始识别/禁用原因);
 *               历史=日期分组。HorizontalPager 保活今日与历史两页
 *@author: 杨帅林
 *@create: 2026/4/11
 **/

private const val TAB_TODAY = 0
private const val TAB_HISTORY = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietRecordScreen(
    goBack: () -> Unit,
    goSetting: () -> Unit = {},
    viewModel: DietRecordViewModel = koinViewModel(),
) {
    val addState by viewModel.addTab.collectAsStateWithLifecycle()
    val todayState by viewModel.todayTab.collectAsStateWithLifecycle()
    val historyState by viewModel.historyTab.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarShow = LocalSnackBarShow.current
    val snackbarHostState = LocalSnackbarHostState.current
    val pagerState = rememberPagerState(initialPage = TAB_TODAY) { 2 }
    val addListState = rememberLazyListState()
    var showAddPage by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showAddPage) {
        showAddPage = false
    }

    // 跨午夜后回到页面时刷新「今天」口径,并顺带清理拍照临时目录
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshTodayDate()
    }

    // 备注放本地状态:进 ViewModel 的 StateFlow 会让整个页面的每个 item 逐字符重组,
    // 只在发起分析/保存时把最终值传给 VM;HorizontalPager 保活使切 Tab 不丢。
    // 传 MutableState 对象而非值:逐键击只有输入框叶子重组,列表其余 item 不被卷入;
    // hasNote 走 derivedStateOf,仅在 空↔非空 翻转时才触发上层重组(驱动主按钮形态)
    val noteState = rememberSaveable { mutableStateOf("") }
    val hasNote by remember { derivedStateOf { noteState.value.isNotBlank() } }

    // 「添加」Tab 的食物项编辑弹窗(index=-1 表示新增)
    var showFoodEditor by remember { mutableStateOf(false) }
    var editingFoodIndex by remember { mutableStateOf(-1) }
    var editingFoodItem by remember { mutableStateOf<RecognizedFoodItem?>(null) }
    if (showFoodEditor) {
        FoodItemEditorSheet(
            initialItem = editingFoodItem,
            onConfirm = { item ->
                if (editingFoodIndex >= 0) {
                    viewModel.updateFoodItem(editingFoodIndex, item)
                } else {
                    viewModel.addFoodItem(item)
                }
                showFoodEditor = false
                editingFoodIndex = -1
                editingFoodItem = null
            },
            onDismiss = {
                showFoodEditor = false
                editingFoodIndex = -1
                editingFoodItem = null
            },
        )
    }

    fun openFoodEditor(index: Int) {
        editingFoodIndex = index
        editingFoodItem = addState.recognizedFoods.getOrNull(index)
        showFoodEditor = true
    }

    // 拍照(TakePicture 全尺寸,委托系统相机,无需 CAMERA 权限)
    val captureFileRef = remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok: Boolean ->
        if (ok) viewModel.onCaptureResult(captureFileRef.value)
    }

    fun launchCamera() {
        val file = ImageCompressor.createCameraCaptureFile(context)
        captureFileRef.value = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePicture.launch(uri)
    }

    // 相册
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onGallerySelected(it) }
    }

    // 跨 Tab 单次事件:保存动线(切「今日」+ 余量 SnackBar)、失败与拍摄异常提示
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DietEvent.RecordSaved -> {
                    noteState.value = ""
                    snackBarShow(
                        event.remainingCalories?.let { "已记录,今日还可摄入 $it kcal" } ?: "已记录"
                    )
                    showAddPage = false
                    pagerState.animateScrollToPage(TAB_TODAY)
                }
                DietEvent.SaveFailed -> snackBarShow("保存失败,请重试")
                DietEvent.CaptureInvalid -> snackBarShow("拍摄失败,请重试或改用相册")
                DietEvent.EditorSaved -> snackBarShow("修改已保存")
            }
        }
    }

    // 删除撤销:SnackBar 存续期 = 撤销窗口(SnackbarDuration.Long = 10s)。
    // 连续删除时取消当前条重发合并计数;被其他 SnackBar 顶掉同样走 commitPending
    var undoSnackbarJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(Unit) {
        viewModel.pendingDeleteRecords.collect { pending ->
            if (pending.isEmpty()) return@collect
            undoSnackbarJob?.cancel()
            undoSnackbarJob = launch {
                val result = snackbarHostState.showSnackbar(
                    message = if (pending.size == 1) "已删除 1 条记录" else "已删除 ${pending.size} 条记录",
                    actionLabel = "撤销",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoDeleteAll()
                } else {
                    viewModel.commitPendingDeletes()
                }
            }
        }
    }

    // 已保存记录编辑器
    editing?.let { session ->
        DietRecordEditorSheet(
            foods = session.foods,
            mealType = session.mealType,
            date = session.date,
            onMealTypeSelected = viewModel::editorSetMealType,
            onDateSelected = viewModel::editorSetDate,
            onUpdateFood = viewModel::editorUpdateFood,
            onRemoveFood = viewModel::editorRemoveFood,
            onAddFood = viewModel::editorAddFood,
            onSave = viewModel::saveEditor,
            onDeleteRecord = viewModel::deleteEditedRecord,
            onDismiss = viewModel::dismissEditor,
        )
    }

    Scaffold(
        topBar = {
            MyTopBar(
                title = if (showAddPage) "记录${addState.selectedMealType.displayName}" else "饮食记录",
                goBack = if (showAddPage) ({ showAddPage = false }) else goBack,
            )
        }
    ) { paddingValues ->
        if (showAddPage) {
            AddTabPage(
                state = addState,
                todayTotalCalories = todayState.totalCalories,
                recommendedCalories = todayState.recommendedCalories,
                noteState = noteState,
                hasNote = hasNote,
                onMealTypeSelected = viewModel::onMealTypeSelected,
                onTakePhoto = { launchCamera() },
                onPickFromGallery = {
                    pickMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClearImage = viewModel::clearImage,
                onStartAnalysis = { viewModel.startAnalysis(noteState.value) },
                onCancelAnalysis = viewModel::cancelAnalysis,
                onDiscardAnalysis = viewModel::discardAnalysis,
                onQuickAddFood = viewModel::addFoodItem,
                onEditFood = ::openFoodEditor,
                onRemoveFood = viewModel::removeFoodItem,
                onClearFoods = viewModel::clearFoods,
                onSave = { viewModel.saveRecord(noteState.value) },
                listState = addListState,
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                DietModeSwitcher(
                    selected = pagerState.currentPage,
                    onSelected = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        TAB_TODAY -> TodayTabPage(
                            state = todayState,
                            onEditRecord = viewModel::openEditor,
                            onGoAdd = { showAddPage = true },
                            goSetting = goSetting,
                        )
                        TAB_HISTORY -> HistoryTabPage(
                            state = historyState,
                            onRangeSelected = viewModel::setHistoryRange,
                            onEditRecord = viewModel::openEditor,
                            onGoAdd = { showAddPage = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DietModeSwitcher(selected: Int, onSelected: (Int) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WeightWiseDimens.PageHorizontal, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            listOf(TAB_TODAY to "今天怎么吃", TAB_HISTORY to "回看记录").forEach { (page, label) ->
                val isSelected = selected == page
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClickLabel = label) { onSelected(page) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.inverseSurface else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.inverseOnSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 11.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTabPage(
    state: AddTabState,
    todayTotalCalories: Int,
    recommendedCalories: Int?,
    noteState: MutableState<String>,
    hasNote: Boolean,
    onMealTypeSelected: (MealType) -> Unit,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onClearImage: () -> Unit,
    onStartAnalysis: () -> Unit,
    onCancelAnalysis: () -> Unit,
    onDiscardAnalysis: () -> Unit,
    onQuickAddFood: (RecognizedFoodItem) -> Unit,
    onEditFood: (Int) -> Unit,
    onRemoveFood: (Int) -> Unit,
    onClearFoods: () -> Unit,
    onSave: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    // D11/OV2A:四输入穷举;isAnalyzing 时按钮由「取消识别」分支覆盖
    val action = dietPrimaryAction(
        hasImage = state.hasImage,
        hasNote = hasNote,
        hasFoods = state.recognizedFoods.isNotEmpty(),
        hasResult = state.aiResponse != null,
    )
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 15.dp, top = 12.dp, end = 15.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 第一步只回答“吃了什么”:常用食物与图片入口保持稳定,不因已选食物而跳变。
        item(key = "food_sources") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AddSectionHeader(
                    title = "添加食物",
                    description = "从常用食物快速添加，或用照片识别",
                )

                if (state.frequentFoods.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "常用食物",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.frequentFoods.forEach { food ->
                                OutlinedButton(
                                    onClick = { onQuickAddFood(food) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Text(
                                        "${food.name} · ${food.estimatedCalories} kcal",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                when {
                    state.selectedImageUri != null -> {
                        GalleryPreview(imageUri = state.selectedImageUri, onClearImage = onClearImage)
                    }
                    state.captureFile != null -> {
                        CapturePreview(file = state.captureFile, onClearImage = onClearImage)
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = onTakePhoto,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            ) {
                                Icon(
                                    Icons.Default.AddAPhoto,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("拍照识别", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onPickFromGallery,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            ) {
                                Icon(
                                    Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("从相册选", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // 快速路径已选区:结果卡不在场时展示(结果卡在场则由结果卡呈现)
        if (state.aiResponse == null && state.recognizedFoods.isNotEmpty()) {
            item(key = "selected_foods") {
                SelectedFoodsSection(
                    foods = state.recognizedFoods,
                    onEdit = onEditFood,
                    onRemove = onRemoveFood,
                    onClear = onClearFoods,
                )
            }
        }

        // 第二步补充上下文。餐次有默认值,备注可跳过,不阻断最快保存路径。
        item(key = "meal_details") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AddSectionHeader(
                    title = "用餐信息",
                    description = "确认餐次，备注可选",
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MealType.entries.forEach { mealType ->
                        FilterChip(
                            selected = state.selectedMealType == mealType,
                            onClick = { onMealTypeSelected(mealType) },
                            label = { Text(mealType.displayName, maxLines = 1) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                NoteField(noteState)
            }
        }

        // 情境化主按钮(D5/D11)
        if (action != DietPrimaryAction.HIDDEN) {
            item(key = "primary_action") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (action) {
                        DietPrimaryAction.SAVE_THIS_MEAL -> {
                            val quickTotal = state.recognizedFoods.sumOf { it.estimatedCalories }
                            Button(
                                onClick = onSave,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isSaving,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                if (state.isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("保存这餐 · $quickTotal kcal", fontWeight = FontWeight.Bold)
                            }
                            if (hasNote) {
                                TextButton(onClick = onStartAnalysis) {
                                    Text("改用文本识别", fontSize = 13.sp)
                                }
                            }
                        }
                        DietPrimaryAction.ANALYZE -> {
                            Button(
                                onClick = if (state.isAnalyzing) onCancelAnalysis else onStartAnalysis,
                                modifier = Modifier.fillMaxWidth(),
                                // 分析中该按钮是「取消识别」,必须可点;空闲时要求有图或有备注
                                enabled = !state.isSaving && (state.isAnalyzing || state.hasImage || hasNote),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                if (state.isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("取消识别")
                                } else {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (state.hasImage) "开始识别" else "文本识别")
                                }
                            }
                            if (state.isAnalyzing) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "识别中,通常需要 10–20 秒 · 可继续改餐次或备注",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // 等待期迷你额度行:把 10–20 秒焦虑转化为预算心智
                                if (recommendedCalories != null && recommendedCalories > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "识别期间:今日还可摄入 ${(recommendedCalories - todayTotalCalories).coerceAtLeast(0)} kcal",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        DietPrimaryAction.DISABLED -> {
                            Button(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("拍照、点常用食物或写备注开始")
                            }
                        }
                        DietPrimaryAction.HIDDEN -> Unit
                    }
                }
            }
        }

        // 结果卡 + 保存(分析完成后自动滚动至此)
        if (state.aiResponse != null) {
            item(key = "ai_result") {
                AiResultSection(
                    trafficLight = state.trafficLight,
                    aiAdvice = state.aiAdvice,
                    foods = state.recognizedFoods,
                    isFallback = state.isFallback,
                    todayTotalCalories = todayTotalCalories,
                    recommendedCalories = recommendedCalories,
                    onEditFood = onEditFood,
                    onRemoveFood = onRemoveFood,
                    onAddFood = { onEditFood(-1) },
                    onDiscard = onDiscardAnalysis,
                )
            }

            item(key = "save_button") {
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.recognizedFoods.isNotEmpty() && !state.isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("保存记录", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // 分析完成滚动至结果卡(结果卡在场时 item 布局:0 输入卡、1 结果卡、2 保存)
    LaunchedEffect(state.aiResponse) {
        if (state.aiResponse != null && state.recognizedFoods.isNotEmpty()) {
            delay(200) // 等结果卡完成组合
            runCatching { listState.animateScrollToItem(1) }
        }
    }
}

@Composable
private fun AddSectionHeader(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 备注输入框:独立叶子组件持有对 [noteState] 的读写,逐键击只重组本组件,
 * 输入卡其余部分(餐次 chips/图片区/常用食物)与下方列表 item 均不受影响。
 */
@Composable
private fun NoteField(noteState: MutableState<String>) {
    OutlinedTextField(
        value = noteState.value,
        onValueChange = { noteState.value = it },
        label = { Text("备注（可选）") },
        placeholder = { Text("例如：只吃了一半") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        minLines = 1,
        maxLines = 3,
    )
}

/** 食物编辑弹窗状态由屏幕宿主持有,编辑器/添加页共用 */
@Composable
private fun GalleryPreview(imageUri: Uri, onClearImage: () -> Unit) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxWidth()) {
        var imageBitmap by remember(imageUri) { mutableStateOf<ImageBitmap?>(null) }
        var loadFailed by remember(imageUri) { mutableStateOf(false) }
        LaunchedEffect(imageUri) {
            imageBitmap = withContext(Dispatchers.IO) {
                val bitmap = runCatching { ImageCompressor.decodeScaled(context, imageUri) }
                    .onFailure { android.util.Log.w("DietRecord", "Failed to decode preview $imageUri", it) }
                    .getOrNull()
                bitmap?.asImageBitmap()
            }
            loadFailed = imageBitmap == null
        }
        val currentBitmap = imageBitmap
        if (currentBitmap != null) {
            Image(
                painter = BitmapPainter(currentBitmap),
                contentDescription = "已选食物图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            PreviewPlaceholder(text = if (loadFailed) "图片加载失败" else "图片加载中…")
        }
        ClearImageButton(onClearImage, Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

@Composable
private fun CapturePreview(file: File, onClearImage: () -> Unit) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxWidth()) {
        var imageBitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
        var loadFailed by remember(file) { mutableStateOf(false) }
        LaunchedEffect(file) {
            imageBitmap = withContext(Dispatchers.IO) {
                val bitmap = runCatching { ImageCompressor.decodeScaled(context, Uri.fromFile(file)) }
                    .onFailure { android.util.Log.w("DietRecord", "Failed to decode capture preview", it) }
                    .getOrNull()
                bitmap?.asImageBitmap()
            }
            loadFailed = imageBitmap == null
        }
        val currentBitmap = imageBitmap
        if (currentBitmap != null) {
            Image(
                painter = BitmapPainter(currentBitmap),
                contentDescription = "拍摄的食物图片",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            PreviewPlaceholder(text = if (loadFailed) "照片加载失败,请重新拍摄" else "照片加载中…")
        }
        ClearImageButton(onClearImage, Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

@Composable
private fun PreviewPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClearImageButton(onClearImage: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClearImage,
        modifier = modifier.size(48.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = "移除图片", modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * 识别结果卡(T4):复用 B 行语言;顶部红绿灯 chip+总热量;宏量图例按合并后 foods 现算;
 * 保存前额度预览条;「放弃本次识别」退出路径(OV10)
 */
@Composable
private fun AiResultSection(
    trafficLight: String,
    aiAdvice: String,
    foods: List<RecognizedFoodItem>,
    isFallback: Boolean,
    todayTotalCalories: Int,
    recommendedCalories: Int?,
    onEditFood: (Int) -> Unit,
    onRemoveFood: (Int) -> Unit,
    onAddFood: () -> Unit,
    onDiscard: () -> Unit,
) {
    val chipColors = lightChipColors(trafficLight)
    val totalCalories = foods.sumOf { it.estimatedCalories }
    val macros = Macros(
        protein = foods.sumOf { it.protein },
        carbs = foods.sumOf { it.carbs },
        fat = foods.sumOf { it.fat },
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 离线兜底信任态:徽章 + 灰化红绿灯 + 置顶核对提示(评审 F8)
            if (isFallback) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                ) {
                    Text(
                        "离线估算:AI 暂不可用,以下为默认值,请核对修改",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(chipColors.container)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(chipColors.dot, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        trafficLightLabel(trafficLight),
                        style = MaterialTheme.typography.labelMedium,
                        color = chipColors.text,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "$totalCalories kcal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (macros.protein > 0 || macros.carbs > 0 || macros.fat > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MacroLegend("蛋白质", "${macros.protein}g", DietMacroColors.Protein.resolve())
                    MacroLegend("碳水", "${macros.carbs}g", DietMacroColors.Carbs.resolve())
                    MacroLegend("脂肪", "${macros.fat}g", DietMacroColors.Fat.resolve())
                }
            }

            if (aiAdvice.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    aiAdvice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            val lightColor = trafficLightColor(trafficLight)
            foods.forEachIndexed { index, food ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = "编辑${food.name}") { onEditFood(index) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 32.dp)
                                .background(lightColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                food.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val detail = buildString {
                                if (food.estimatedGrams > 0) append("${food.estimatedGrams}g · ")
                                append("${food.estimatedCalories}kcal")
                                if (food.isManuallyAdded) append(" · 手动添加")
                            }
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onRemoveFood(index) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "删除${food.name}",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onAddFood,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加食物")
            }

            // 保存前额度预览:延续编辑器「不突袭」原则,保存前看到这餐的影响
            if (recommendedCalories != null && recommendedCalories > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val after = recommendedCalories - todayTotalCalories - totalCalories
                Text(
                    if (after >= 0) "这餐 $totalCalories kcal · 保存后今日还可摄入 $after kcal"
                    else "这餐 $totalCalories kcal · 保存后今日将超出 ${-after} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

            TextButton(
                onClick = onDiscard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("放弃本次识别", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TodayTabPage(
    state: TodayTabState,
    onEditRecord: (DietRecord) -> Unit,
    onGoAdd: () -> Unit,
    goSetting: () -> Unit,
) {
    val mealTypeByName = remember { MealType.entries.associateBy { it.name } }
    // 按餐次分组结果 remember 键住记录列表：无关重组（额度刷新等）不必重建分组；
    // LazyListScope 构建块不是 @Composable 上下文，remember 须放在 LazyColumn 之外
    val groupedByMeal = remember(state.records) { state.records.groupBy { it.mealType } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = WeightWiseDimens.PageHorizontal,
            vertical = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(WeightWiseDimens.SectionGap),
    ) {
        item(key = "today_summary") {
            IntakeHero(
                totalCalories = state.totalCalories,
                recommendedCalories = state.recommendedCalories,
                macros = state.macros,
                isEmpty = state.records.isEmpty(),
                goSetting = goSetting,
            )
        }

        if (state.records.isEmpty()) {
            item(key = "empty_today") {
                WeightWiseEmptyState(
                    icon = MealType.LUNCH.icon,
                    title = "今天还没记录",
                    message = "拍照识别，或从常用食物快速添加",
                    actionLabel = "去记一笔",
                    onAction = onGoAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 310.dp)
                        .padding(horizontal = 24.dp),
                )
            }
        } else {
            // 按餐次分组:组头=图标+餐次+首条时间+小计（分组结果见 LazyColumn 上方的 remember）
            MealType.entries.forEach { mealType ->
                val mealRecords = groupedByMeal[mealType.name] ?: return@forEach
                val subtotal = mealRecords.sumOf { it.estimatedCalories }
                val firstTime = TimeUtils.convertMillisToHM(mealRecords.minOf { it.timestamp })
                item(key = "meal_header_${mealType.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            mealType.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            mealType.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            firstTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "$subtotal kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(
                    count = mealRecords.size,
                    key = { i -> "today_${mealRecords[i].id}" },
                ) { i ->
                    val record = mealRecords[i]
                    val texts = rememberRecordTexts(record, mealLabel = null)
                    DietRecordRow(
                        record = record,
                        title = texts.first,
                        subtitle = texts.second,
                        onClick = { onEditRecord(record) },
                    )
                }
            }
            item(key = "go_add_footer") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClickLabel = "补记一笔") { onGoAdd() }
                        .padding(vertical = 12.dp)
                        .minimumInteractiveComponentSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "补记一笔",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/**
 * Hero 预算卡(T1/D3):大数字=已记录;环填充=已用比例、环色=额度状态(IntakeRingColors);
 * 满配/接近/超支/档案缺失四态;零记录日隐藏堆叠条
 */
@Composable
private fun IntakeHero(
    totalCalories: Int,
    recommendedCalories: Int?,
    macros: DailyMacros?,
    isEmpty: Boolean,
    goSetting: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 30.dp, bottomStart = 30.dp, bottomEnd = 8.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Column(modifier = Modifier.padding(WeightWiseDimens.CardPadding)) {
            if (recommendedCalories != null && recommendedCalories > 0) {
                val status = CalorieCalculator.intakeStatus(totalCalories, recommendedCalories)
                val remaining = recommendedCalories - totalCalories
                val over = status == IntakeStatus.OVER
                val usedPercent = ((totalCalories.toDouble() / recommendedCalories) * 100).roundToInt()
                val today = remember { LocalDate.now() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RingBox(
                        usedPercent = usedPercent,
                        color = status.ringColor(),
                        over = over,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "今日饮食 / ${today.monthValue}月${today.dayOfMonth}日",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.58f),
                        )
                        Text(
                            if (over) "超出 ${-remaining}" else "剩余 $remaining",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (over) IntakeRingColors.Over.resolve()
                            else MaterialTheme.colorScheme.inversePrimary,
                            maxLines = 1,
                        )
                        Text(
                            "已摄入 $totalCalories kcal  ·  目标 $recommendedCalories kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.68f),
                            maxLines = 1,
                        )
                    }
                }

                if (!isEmpty) {
                    val weights = macros
                        ?.takeIf { it.hasMacroData }
                        ?.let { DailyMacroAggregator.macroCalorieWeights(it.protein, it.carbs, it.fat) }
                    if (macros != null && macros.hasMacroData && weights != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        MacroStackedBar(weights)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            MacroLegend("蛋白质", "${macros.protein}g", DietMacroColors.Protein.resolve(), MaterialTheme.colorScheme.inverseOnSurface)
                            MacroLegend("碳水", "${macros.carbs}g", DietMacroColors.Carbs.resolve(), MaterialTheme.colorScheme.inverseOnSurface)
                            MacroLegend("脂肪", "${macros.fat}g", DietMacroColors.Fat.resolve(), MaterialTheme.colorScheme.inverseOnSurface)
                        }
                        if (macros.skippedRecords > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${macros.skippedRecords} 条记录数据异常未计入宏量",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.62f),
                            )
                        }
                    } else if (macros != null && !macros.hasMacroData) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "宏量数据 —(本日记录早于宏量统计功能,未记录该数据)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.62f),
                        )
                    }
                }
            } else {
                // 档案不全降级(D6/4A):大数字回退已摄入,环置灰隐藏,引导去设置页
                Text(
                    "今日已摄入",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.62f),
                )
                Text(
                    "$totalCalories kcal",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "今天 · 补全身体档案后开启每日额度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.62f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClickLabel = "去补全档案") { goSetting() }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                        .minimumInteractiveComponentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "补全档案,开启每日额度",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/** 状态圆环:填充=已用比例,环色=额度状态;超支画满;环心中性「已用 N%」 */
@Composable
private fun RingBox(
    usedPercent: Int,
    color: Color,
    over: Boolean,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = (usedPercent.coerceIn(0, 100)) / 100f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "intakeRing",
    )
    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Canvas 内是 DrawScope 非组合期,轨道色先在组合期解析
        val trackColor = IntakeRingColors.Track.resolve()
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = if (over) 360f else animatedFraction * 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            if (over) "已用\n$usedPercent%" else "已用 $usedPercent%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MacroStackedBar(weights: Triple<Float, Float, Float>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        // weight(0f) 会抛 IllegalArgumentException:零权重段直接不渲染
        // (单食物常见:如纯碳水食物蛋白/脂肪为 0)
        if (weights.first > 0f) {
            Box(
                modifier = Modifier
                    .weight(weights.first)
                    .fillMaxHeight()
                    .background(DietMacroColors.Protein.resolve())
            )
        }
        if (weights.second > 0f) {
            Box(
                modifier = Modifier
                    .weight(weights.second)
                    .fillMaxHeight()
                    .background(DietMacroColors.Carbs.resolve())
            )
        }
        if (weights.third > 0f) {
            Box(
                modifier = Modifier
                    .weight(weights.third)
                    .fillMaxHeight()
                    .background(DietMacroColors.Fat.resolve())
            )
        }
    }
}

@Composable
private fun MacroLegend(
    label: String,
    value: String,
    color: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            "$label $value",
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.72f),
        )
    }
}

/** 从记录 JSON 解析食物并推导行标题/副标(食物名是视觉主角);解析失败回退通用文案 */
@Composable
private fun rememberRecordTexts(record: DietRecord, mealLabel: String?): Pair<String, String> {
    val json: kotlinx.serialization.json.Json = koinInject()
    val foods = remember(record.id, record.recognizedFoodJson) {
        runCatching {
            json.decodeFromString<List<RecognizedFoodItem>>(record.recognizedFoodJson)
        }.onFailure { android.util.Log.w("DietRecord", "行文案:记录 JSON 解析失败 ${record.id}", it) }
            .getOrNull()
    }
    return remember(foods, mealLabel, record.userInput) {
        when {
            foods.isNullOrEmpty() -> {
                (mealLabel ?: "饮食记录") to record.userInput
            }
            foods.size == 1 -> {
                val f = foods.first()
                val sub = buildString {
                    if (mealLabel != null) append("$mealLabel · ")
                    if (f.estimatedGrams > 0) append("${f.estimatedGrams}g")
                    if (record.userInput.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(record.userInput)
                    }
                    if (isEmpty()) append("${f.estimatedCalories} kcal")
                }
                f.name to sub
            }
            else -> {
                val sub = buildString {
                    if (mealLabel != null) append("$mealLabel")
                    if (record.userInput.isNotBlank()) {
                        append(" · ")
                        append(record.userInput)
                    }
                }
                "${foods.first().name} 等 ${foods.size} 项" to sub
            }
        }
    }
}

/** B 行(D7/D8):色条+40dp 缩略图(有图才出现)+食物名标题+kcal;点击整行进编辑器 */
@Composable
private fun DietRecordRow(
    record: DietRecord,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val lightColor = trafficLightColor(record.trafficLight)
    val thumbnailCache = koinInject<DietThumbnailCache>()
    val thumb by produceState<ImageBitmap?>(initialValue = null, record.imageUri) {
        if (record.imageUri.isNotBlank()) {
            value = thumbnailCache.get(record.imageUri)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClickLabel = "编辑记录") { onClick() }
            .padding(start = 0.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 0.dp)
                    .size(width = 4.dp, height = 40.dp)
                    .background(lightColor, RoundedCornerShape(0.dp, 3.dp, 3.dp, 0.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            if (record.imageUri.isNotBlank()) {
                val currentThumb = thumb
                if (currentThumb != null) {
                    Image(
                        painter = BitmapPainter(currentThumb),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${record.estimatedCalories} kcal",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                trafficLightLabel(record.trafficLight),
                style = MaterialTheme.typography.bodySmall,
                color = lightColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HistoryTabPage(
    state: HistoryTabState,
    onRangeSelected: (Int) -> Unit,
    onEditRecord: (DietRecord) -> Unit,
    onGoAdd: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val mealTypeByName = remember { MealType.entries.associateBy { it.name } }
    val recordedDays = remember(state.days) { state.days.filter { it.records.isNotEmpty() } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 时间口径:复用主屏 ScopeSelector 的心智(近30天/近3月/近6月)
        item(key = "range_selector") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(30 to "近30天", 90 to "近3月", 180 to "近6月").forEach { (days, label) ->
                    FilterChip(
                        selected = state.rangeDays == days,
                        onClick = { onRangeSelected(days) },
                        label = { Text(label, fontSize = 13.sp) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    )
                }
            }
        }

        if (recordedDays.isEmpty()) {
            item(key = "empty_history") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            MealType.LUNCH.icon,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "该时段暂无记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "记录一餐后，这里会按日期汇总",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onGoAdd, shape = RoundedCornerShape(12.dp)) {
                        Text("去记第一笔")
                    }
                }
            }
        } else {
            item(key = "history_count") {
                Text(
                    "有记录 ${recordedDays.size} 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(recordedDays, key = { "day_${it.date}" }) { day ->
                val dayLight = day.trafficLight.orEmpty()
                // 日期头:人性化日期+当日红绿灯点+日合计
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(trafficLightColor(dayLight), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            TimeUtils.humanizeDate(today, day.date),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            trafficLightLabel(dayLight),
                            style = MaterialTheme.typography.labelSmall,
                            color = trafficLightColor(dayLight),
                            maxLines = 1,
                        )
                    }
                    Text(
                        "${day.totalCalories} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                day.records.forEach { record ->
                    val texts = rememberRecordTexts(
                        record,
                        mealLabel = mealTypeByName[record.mealType]?.displayName,
                    )
                    DietRecordRow(
                        record = record,
                        title = texts.first,
                        subtitle = texts.second,
                        onClick = { onEditRecord(record) },
                    )
                }
            }
        }
    }
}
