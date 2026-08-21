package com.example.weight.ui.diet

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.weight.data.diet.Macros
import com.example.weight.data.diet.MealType
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.ui.common.MyTopBar
import com.example.weight.util.CalorieCalculator
import com.example.weight.util.ImageCompressor
import com.example.weight.util.IntakeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.io.File

/**
 *@description: 饮食记录主界面（v1.5 三 Tab 重构：添加/今日/历史）。
 *               HorizontalPager 保活三页：切 Tab 输入态（备注）不丢；
 *               撤销 SnackBar、编辑器、保存动线均挂宿主级
 *@author: 杨帅林
 *@create: 2026/4/11
 **/

private const val TAB_ADD = 0
private const val TAB_TODAY = 1
private const val TAB_HISTORY = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietRecordScreen(
    goBack: () -> Unit,
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
    val pagerState = rememberPagerState(initialPage = TAB_ADD) { 3 }
    val addListState = rememberLazyListState()

    // 跨午夜后回到页面时刷新「今天」口径，并顺带清理拍照临时目录
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshTodayDate()
    }

    // 备注放本地状态：进 ViewModel 的 StateFlow 会让整个页面的每个 item 逐字符重组，
    // 只在发起分析/保存时把最终值传给 VM；HorizontalPager 保活使切 Tab 不丢
    var userNote by rememberSaveable { mutableStateOf("") }

    // 「添加」Tab 的食物项编辑弹窗（index=-1 表示新增）
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

    // 拍照（TakePicture 全尺寸，委托系统相机，无需 CAMERA 权限）
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

    // 跨 Tab 单次事件：保存动线（切「今日」+ 余量 SnackBar）、失败与拍摄异常提示
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DietEvent.RecordSaved -> {
                    userNote = ""
                    snackBarShow(
                        event.remainingCalories?.let { "已记录，今日还可摄入 $it kcal" } ?: "已记录"
                    )
                    pagerState.animateScrollToPage(TAB_TODAY)
                }
                DietEvent.SaveFailed -> snackBarShow("保存失败，请重试")
                DietEvent.CaptureInvalid -> snackBarShow("拍摄失败，请重试或改用相册")
                DietEvent.EditorSaved -> snackBarShow("修改已保存")
            }
        }
    }

    // 删除撤销：SnackBar 存续期 = 撤销窗口（SnackbarDuration.Long = 10s）。
    // 连续删除时取消当前条重发合并计数；被其他 SnackBar 顶掉同样走 commitPending（宁可慢删不误删：
    // 记录行已即时删除，此处只提交图片清理）
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
            MyTopBar(title = "饮食记录", goBack = goBack)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == TAB_ADD,
                    onClick = { scope.launch { pagerState.animateScrollToPage(TAB_ADD) } },
                    text = { Text("添加") },
                )
                Tab(
                    selected = pagerState.currentPage == TAB_TODAY,
                    onClick = { scope.launch { pagerState.animateScrollToPage(TAB_TODAY) } },
                    text = { Text("今日") },
                )
                Tab(
                    selected = pagerState.currentPage == TAB_HISTORY,
                    onClick = { scope.launch { pagerState.animateScrollToPage(TAB_HISTORY) } },
                    text = { Text("历史") },
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    TAB_ADD -> AddTabPage(
                        state = addState,
                        userNote = userNote,
                        onUserNoteChange = { userNote = it },
                        onMealTypeSelected = viewModel::onMealTypeSelected,
                        onTakePhoto = { launchCamera() },
                        onPickFromGallery = {
                            pickMedia.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onClearImage = viewModel::clearImage,
                        onStartAnalysis = { viewModel.startAnalysis(userNote) },
                        onCancelAnalysis = viewModel::cancelAnalysis,
                        onQuickAddFood = viewModel::addFoodItem,
                        onEditFood = ::openFoodEditor,
                        onRemoveFood = viewModel::removeFoodItem,
                        onSave = { viewModel.saveRecord(userNote) },
                        listState = addListState,
                    )
                    TAB_TODAY -> TodayTabPage(
                        state = todayState,
                        onEditRecord = viewModel::openEditor,
                        onDeleteRecord = viewModel::deleteRecord,
                        onGoAdd = { scope.launch { pagerState.animateScrollToPage(TAB_ADD) } },
                    )
                    TAB_HISTORY -> HistoryTabPage(
                        state = historyState,
                        onRangeSelected = viewModel::setHistoryRange,
                        onEditRecord = viewModel::openEditor,
                        onDeleteRecord = viewModel::deleteRecord,
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
    userNote: String,
    onUserNoteChange: (String) -> Unit,
    onMealTypeSelected: (MealType) -> Unit,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onClearImage: () -> Unit,
    onStartAnalysis: () -> Unit,
    onCancelAnalysis: () -> Unit,
    onQuickAddFood: (RecognizedFoodItem) -> Unit,
    onEditFood: (Int) -> Unit,
    onRemoveFood: (Int) -> Unit,
    onSave: () -> Unit,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 输入卡：餐次 + 常用食物（首屏可达，兑现「快速添加优先级最高」）+ 图片 + 备注
        item(key = "input_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MealType.entries.forEach { mealType ->
                            FilterChip(
                                selected = state.selectedMealType == mealType,
                                onClick = { onMealTypeSelected(mealType) },
                                label = { Text(mealType.displayName, fontSize = 13.sp, maxLines = 1) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }

                    // 常用食物区：点击以历史中位数克数/热量直接加入下方列表（离线零 AI）
                    if (state.frequentFoods.isNotEmpty()) {
                        Column {
                            Text(
                                "常用食物",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                state.frequentFoods.forEach { food ->
                                    OutlinedButton(
                                        onClick = { onQuickAddFood(food) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            "${food.name} · ${food.estimatedCalories}",
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 图片区：无图且已有食物（纯快速添加）时折叠为一行入口
                    when {
                        state.selectedImageUri != null -> {
                            GalleryPreview(imageUri = state.selectedImageUri, onClearImage = onClearImage)
                        }
                        state.captureFile != null -> {
                            CapturePreview(file = state.captureFile, onClearImage = onClearImage)
                        }
                        state.recognizedFoods.isEmpty() -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(onClick = onTakePhoto, shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("拍照")
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                OutlinedButton(onClick = onPickFromGallery, shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("相册")
                                }
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = onTakePhoto,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("拍照识别（可选）", fontSize = 13.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = userNote,
                        onValueChange = onUserNoteChange,
                        label = { Text("添加备注（如：只吃了一半）") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }

        // 分析卡：单一入口（有图走图片识别，无图备注非空走文本识别），分析中可取消
        item(key = "analyze_button") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = if (state.isAnalyzing) onCancelAnalysis else onStartAnalysis,
                    modifier = Modifier.fillMaxWidth(),
                    // 分析中该按钮是「取消分析」，必须可点；空闲时要求有图或有备注
                    enabled = !state.isSaving && (state.isAnalyzing || state.hasImage || userNote.isNotBlank()),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (state.isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("取消分析")
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.hasImage) "开始分析" else "文本识别")
                    }
                }
                if (state.isAnalyzing) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "识别中，通常需要 10–20 秒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 结果卡 + 保存（分析完成后自动滚动至此）
        if (state.aiResponse != null) {
            item(key = "ai_result") {
                AiResultSection(
                    trafficLight = state.trafficLight,
                    aiAdvice = state.aiAdvice,
                    foods = state.recognizedFoods,
                    isFallback = state.isFallback,
                    macros = state.aiResponse?.macros ?: Macros(),
                    onEditFood = onEditFood,
                    onRemoveFood = onRemoveFood,
                    onAddFood = { onEditFood(-1) },
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

    // P9 真正修复：分析完成滚动至结果卡
    LaunchedEffect(state.aiResponse) {
        if (state.aiResponse != null && state.recognizedFoods.isNotEmpty()) {
            delay(200) // 等结果卡完成组合
            runCatching { listState.animateScrollToItem(2) }
        }
    }
}

/** 食物编辑弹窗状态由屏幕宿主持有，编辑器/添加页共用 */
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
                    .clip(RoundedCornerShape(16.dp)),
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
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            PreviewPlaceholder(text = if (loadFailed) "照片加载失败，请重新拍摄" else "照片加载中…")
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
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClearImageButton(onClearImage: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClearImage,
        modifier = modifier
            .size(32.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                CircleShape
            ),
    ) {
        Icon(Icons.Default.Close, contentDescription = "移除图片", modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AiResultSection(
    trafficLight: String,
    aiAdvice: String,
    foods: List<RecognizedFoodItem>,
    isFallback: Boolean,
    macros: com.example.weight.data.diet.Macros,
    onEditFood: (Int) -> Unit,
    onRemoveFood: (Int) -> Unit,
    onAddFood: () -> Unit,
) {
    val lightColor = trafficLightColor(trafficLight)
    val lightLabel = trafficLightLabel(trafficLight)
    val totalCalories = foods.sumOf { it.estimatedCalories }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 离线兜底信任态：徽章 + 灰化红绿灯 + 置顶核对提示（评审 F8）
            if (isFallback) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                ) {
                    Text(
                        "离线估算：AI 暂不可用，以下为默认值，请核对修改",
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (isFallback) lightColor.copy(alpha = 0.4f) else lightColor,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        lightLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isFallback) lightColor.copy(alpha = 0.6f) else lightColor,
                        fontWeight = FontWeight.Bold,
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
                    MacroChip("蛋白质", "${macros.protein}g", Color(0xFF42A5F5))
                    MacroChip("碳水", "${macros.carbs}g", Color(0xFFFFA726))
                    MacroChip("脂肪", "${macros.fat}g", Color(0xFFEF5350))
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

            foods.forEachIndexed { index, food ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            food.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        val detail = buildString {
                            append("${food.estimatedGrams}g · ${food.estimatedCalories}kcal")
                            if (food.category.isNotBlank()) append(" · ${food.category}")
                            if (food.protein > 0 || food.carbs > 0 || food.fat > 0) {
                                append(" · 蛋白${food.protein} 碳水${food.carbs} 脂肪${food.fat}")
                            }
                        }
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row {
                        IconButton(onClick = { onEditFood(index) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "编辑",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { onRemoveFood(index) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
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
        }
    }
}

@Composable
private fun TodayTabPage(
    state: TodayTabState,
    onEditRecord: (DietRecord) -> Unit,
    onDeleteRecord: (DietRecord) -> Unit,
    onGoAdd: () -> Unit,
) {
    val mealTypeByName = remember { MealType.entries.associateBy { it.name } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "today_summary") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "今日饮食",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.recommendedCalories != null && state.recommendedCalories > 0) {
                        IntakeProgressContent(intake = state.totalCalories, recommended = state.recommendedCalories)
                    } else {
                        Text(
                            "已摄入: ${state.totalCalories} kcal",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "补全设置中的年龄、性别与活动水平后，可获得个性化建议摄入量",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 每日宏量合计：无宏量数据日显示「—」降级；混合日标注部分统计
                    state.macros?.let { macros ->
                        Spacer(modifier = Modifier.height(12.dp))
                        DailyMacrosRow(macros)
                    }
                }
            }
        }

        if (state.records.isEmpty()) {
            item(key = "empty_today") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "今天还没有记录，去记一笔吧！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                    OutlinedButton(onClick = onGoAdd, shape = RoundedCornerShape(12.dp)) {
                        Text("去记一笔")
                    }
                }
            }
        } else {
            // 按餐次分组
            val grouped = state.records.groupBy { it.mealType }
            MealType.entries.forEach { mealType ->
                val mealRecords = grouped[mealType.name] ?: return@forEach
                item(key = "meal_header_${mealType.name}") {
                    Text(
                        mealType.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(
                    count = mealRecords.size,
                    key = { i -> "today_${mealRecords[i].id}" },
                ) { i ->
                    val record = mealRecords[i]
                    DietRecordRow(
                        record = record,
                        mealLabel = null,
                        onClick = { onEditRecord(record) },
                        onDelete = { onDeleteRecord(record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyMacrosRow(macros: DailyMacros) {
    Column {
        if (macros.hasMacroData) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MacroChip("蛋白质", "${macros.protein}g", Color(0xFF42A5F5))
                MacroChip("碳水", "${macros.carbs}g", Color(0xFFFFA726))
                MacroChip("脂肪", "${macros.fat}g", Color(0xFFEF5350))
            }
            if (macros.skippedRecords > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${macros.skippedRecords} 条记录数据异常未计入宏量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                "宏量营养素 —（本日记录早于宏量统计功能，未记录该数据）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryTabPage(
    state: HistoryTabState,
    onRangeSelected: (Int) -> Unit,
    onEditRecord: (DietRecord) -> Unit,
    onDeleteRecord: (DietRecord) -> Unit,
) {
    val mealTypeByName = remember { MealType.entries.associateBy { it.name } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 时间口径：复用主屏 ScopeSelector 的心智（近30天/近3月/近6月）
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
                    )
                }
            }
        }

        if (state.records.isEmpty()) {
            item(key = "empty_history") {
                Text(
                    "该时段暂无记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                )
            }
        } else {
            val byDate = state.records.groupBy { it.date }
            byDate.forEach { (date, records) ->
                item(key = "date_header_$date") {
                    val dayTotal = records.sumOf { it.estimatedCalories }
                    val dayLight = state.dayLights[date]
                    val lightColor = dayLight?.let { trafficLightColor(it) } ?: Color.Gray
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(lightColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                date,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            "$dayTotal kcal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(
                    count = records.size,
                    key = { i -> "history_${records[i].id}" },
                ) { i ->
                    val record = records[i]
                    DietRecordRow(
                        record = record,
                        mealLabel = mealTypeByName[record.mealType]?.displayName ?: record.mealType,
                        onClick = { onEditRecord(record) },
                        onDelete = { onDeleteRecord(record) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DietRecordRow(
    record: DietRecord,
    mealLabel: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val lightColor = trafficLightColor(record.trafficLight)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(lightColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    mealLabel ?: "饮食记录",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (record.userInput.isNotBlank()) {
                    Text(
                        record.userInput,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${record.estimatedCalories} kcal",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑记录",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除记录",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** 已摄入/建议摄入进度条：进度封顶 100%，超支用状态色与文案表达 */
@Composable
private fun IntakeProgressContent(intake: Int, recommended: Int) {
    val status = CalorieCalculator.intakeStatus(intake, recommended)
    val statusColor = when (status) {
        IntakeStatus.ENOUGH -> trafficLightColor("GREEN")
        IntakeStatus.NEAR_LIMIT -> trafficLightColor("YELLOW")
        IntakeStatus.OVER -> trafficLightColor("RED")
    }
    val remaining = recommended - intake
    val statusDetail = when (status) {
        IntakeStatus.ENOUGH -> "还可摄入 $remaining kcal"
        IntakeStatus.NEAR_LIMIT -> "今日额度快用完了"
        IntakeStatus.OVER -> "已超出建议 ${-remaining} kcal"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("已摄入 ", style = MaterialTheme.typography.bodyLarge)
            Text(
                "$intake",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor,
            )
            Text(
                " / 建议 $recommended kcal",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                status.label,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    val progress = (intake.toFloat() / recommended).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "intakeProgress",
    )
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = statusColor,
        trackColor = statusColor.copy(alpha = 0.15f),
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        statusDetail,
        style = MaterialTheme.typography.bodySmall,
        color = statusColor,
    )
}

@Composable
private fun MacroChip(label: String, value: String, color: Color) {
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
