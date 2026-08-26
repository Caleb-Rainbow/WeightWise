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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddTabPage(
    state: AddTabState,
    todayTotalCalories: Int,
    recommendedCalories: Int?,
    noteState: MutableState<String>,
    hasNote: Boolean,
    onMealTypeSelected: (MealType) -> Unit,
    onDateSelected: (String) -> Unit,
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
    // 额度相关文案(等待期迷你行/保存前预览)是今天口径,补记别的日期时换文案
    val today = remember { LocalDate.now() }
    val isAddToday = state.date == TimeUtils.getCurrentDate()
    val addDateLabel: String? = if (isAddToday) null else TimeUtils.humanizeDate(today, state.date)
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

        // 第二步补充上下文。餐次有默认值,日期默认今天(可改过去日期补记),备注可跳过,不阻断最快保存路径。
        item(key = "meal_details") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AddSectionHeader(
                    title = "用餐信息",
                    description = "确认餐次与日期，备注可选",
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
                // 补记入口:与编辑器同款交互;显示走人性化文案(今天/昨天/M月d日 周X),仅展示不回传
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = TimeUtils.convertDateToUtcMillis(state.date)
                )
                DatePickerDocked(
                    modifier = Modifier.fillMaxWidth(),
                    selectedDate = TimeUtils.humanizeDate(today, state.date),
                    datePickerState = datePickerState,
                    label = "用餐日期",
                    onDetermine = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onDateSelected(TimeUtils.convertUtcMillisToDate(millis))
                        }
                    },
                )
                NoteField(noteState)
            }
        }

        // 情境化主按钮(D5/D11)
        if (action != DietPrimaryAction.HIDDEN) {
            item(key = "primary_action") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        // 识别中是最高优先级渲染分支,必须覆盖一切输入组合:
                        // SAVE_THIS_MEAL 组合(常用食物+备注)若不覆盖,「改用文本识别」
                        // 点击后界面零反馈会诱发连点重发请求,且「保存这餐」在 AI 结果
                        // 合并前仍可误点
                        state.isAnalyzing -> AnalyzingPrimaryButton(
                            onCancelAnalysis = onCancelAnalysis,
                            recommendedCalories = recommendedCalories,
                            todayTotalCalories = todayTotalCalories,
                            isAddToday = isAddToday,
                        )
                        action == DietPrimaryAction.SAVE_THIS_MEAL -> {
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
                        action == DietPrimaryAction.ANALYZE -> {
                            Button(
                                onClick = onStartAnalysis,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.isSaving && (state.hasImage || hasNote),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (state.hasImage) "开始识别" else "文本识别")
                            }
                        }
                        action == DietPrimaryAction.DISABLED -> {
                            Button(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("拍照、点常用食物或写备注开始")
                            }
                        }
                        else -> Unit
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
                    addDateLabel = addDateLabel,
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

/**
 * 识别中的主按钮形态(D11 高优先级分支):进度圈+「取消识别」+等待文案+迷你额度行。
 * 与输入组合无关——任何 action 下分析进行中都渲染本组件,保证点击识别后必有反馈
 */
@Composable
internal fun AnalyzingPrimaryButton(
    onCancelAnalysis: () -> Unit,
    recommendedCalories: Int?,
    todayTotalCalories: Int,
    isAddToday: Boolean,
) {
    Button(
        onClick = onCancelAnalysis,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("取消识别")
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        "识别中,通常需要 10–20 秒 · 可继续改餐次或备注",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // 等待期迷你额度行:把 10–20 秒焦虑转化为预算心智;补记别的日期时今日额度不适用,隐藏
    if (isAddToday && recommendedCalories != null && recommendedCalories > 0) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "识别期间:今日还可摄入 ${(recommendedCalories - todayTotalCalories).coerceAtLeast(0)} kcal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun AddSectionHeader(
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
internal fun NoteField(noteState: MutableState<String>) {
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
internal fun GalleryPreview(imageUri: Uri, onClearImage: () -> Unit) {
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
internal fun CapturePreview(file: File, onClearImage: () -> Unit) {
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
internal fun PreviewPlaceholder(text: String) {
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
internal fun ClearImageButton(onClearImage: () -> Unit, modifier: Modifier = Modifier) {
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
