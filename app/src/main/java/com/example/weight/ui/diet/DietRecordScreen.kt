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
import androidx.compose.material3.rememberDatePickerState
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
import com.example.weight.ui.common.DatePickerDocked
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
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject


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
                        savedMessage(event.date, LocalDate.now(), event.remainingCalories)
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
                onDateSelected = viewModel::onDateSelected,
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