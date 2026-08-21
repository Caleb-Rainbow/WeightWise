package com.example.weight.ui.diet

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.ui.common.AppPermissions
import com.example.weight.ui.common.MyTopBar
import com.example.weight.ui.common.PermissionOutcome
import com.example.weight.ui.common.rememberPermissionRequester
import com.example.weight.util.CalorieCalculator
import com.example.weight.util.ImageCompressor
import com.example.weight.util.IntakeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

/**
 *@description: 饮食记录主界面
 *@author: 杨帅林
 *@create: 2026/4/11
 **/

private val TrafficLightColorMap = mapOf(
    "GREEN" to Color(0xFF4CAF50),
    "YELLOW" to Color(0xFFFFC107),
    "RED" to Color(0xFFF44336),
)

private val TrafficLightLabelMap = mapOf(
    "GREEN" to "健康饮食",
    "YELLOW" to "尚可",
    "RED" to "放纵一下",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietRecordScreen(
    goBack: () -> Unit,
    viewModel: DietRecordViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 跨午夜后回到页面时刷新“今天”的口径，今日记录/热量合计随日期键自动切换
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshTodayDate()
    }
    var showFoodEditor by remember { mutableStateOf(false) }
    var editingFoodIndex by remember { mutableStateOf(-1) }
    var editingFoodItem by remember { mutableStateOf<RecognizedFoodItem?>(null) }
    // 备注放本地状态：进 ViewModel 的 StateFlow 会让整个页面的每个 item 逐字符重组，
    // 只在发起分析/保存时把最终值传给 VM
    var userNote by rememberSaveable { mutableStateOf("") }

    // 拍照
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { viewModel.onBitmapCaptured(it) }
    }

    // 相机权限：软拒绝给提示（可改用相册），永久拒绝引导去系统设置
    var showCameraSettingsGuide by remember { mutableStateOf(false) }
    val snackBarShow = LocalSnackBarShow.current
    val cameraPermissionRequester = rememberPermissionRequester(
        Manifest.permission.CAMERA
    ) { outcome ->
        when (outcome) {
            PermissionOutcome.GRANTED -> takePicture.launch(null)
            PermissionOutcome.DENIED ->
                snackBarShow("需要相机权限才能拍照识别，可重新点击拍照再次授权，或改用相册")
            PermissionOutcome.PERMANENTLY_DENIED -> showCameraSettingsGuide = true
        }
    }

    fun launchCamera() {
        if (AppPermissions.isGranted(context, Manifest.permission.CAMERA)) {
            takePicture.launch(null)
        } else {
            cameraPermissionRequester()
        }
    }

    if (showCameraSettingsGuide) {
        AlertDialog(
            onDismissRequest = { showCameraSettingsGuide = false },
            title = { Text("需要相机权限") },
            text = { Text("你已选择\"不再询问\"，无法在应用内弹窗授权。请到系统设置中开启\"相机\"权限，或改用相册选图。") },
            confirmButton = {
                TextButton(onClick = {
                    showCameraSettingsGuide = false
                    AppPermissions.openAppSettings(context)
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showCameraSettingsGuide = false }) { Text("取消") }
            },
        )
    }

    // 相册
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onImageSelected(it) }
    }

    // 食物编辑弹窗
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

    Scaffold(
        topBar = {
            MyTopBar(title = "饮食记录", goBack = goBack)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 餐次选择
            item(key = "meal_type") {
                MealTypeSelector(
                    selectedMealType = state.selectedMealType,
                    onMealTypeSelected = viewModel::onMealTypeSelected,
                )
            }

            // 图片选择区
            item(key = "image_picker") {
                ImagePickerSection(
                    imageUri = state.selectedImageUri,
                    hasBitmap = state.hasCapturedBitmap,
                    capturedBitmap = viewModel.previewBitmap,
                    onTakePhoto = { launchCamera() },
                    onPickFromGallery = {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onClearImage = { viewModel.clearImage() },
                )
            }

            // 用户备注
            item(key = "user_note") {
                OutlinedTextField(
                    value = userNote,
                    onValueChange = { userNote = it },
                    label = { Text("添加备注（如：只吃了一半）") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // AI 识别按钮
            item(key = "analyze_button") {
                Button(
                    onClick = { viewModel.analyzeImage(userNote) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isAnalyzing && (state.selectedImageUri != null || state.hasCapturedBitmap),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (state.isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 分析中...")
                    } else {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI 识别")
                    }
                }
            }

            // 纯文本模式
            if (!state.hasCapturedBitmap && state.selectedImageUri == null && userNote.isNotBlank()) {
                item(key = "text_analyze_button") {
                    OutlinedButton(
                        onClick = { viewModel.analyzeTextOnly(userNote) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isAnalyzing,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("纯文本分析")
                    }
                }
            }

            // AI 分析结果
            if (state.aiResponse != null) {
                item(key = "ai_result") {
                    AiResultSection(
                        trafficLight = state.trafficLight,
                        aiAdvice = state.aiAdvice,
                        foods = state.recognizedFoods,
                        totalCalories = state.editableCalories,
                        macros = state.macros,
                        onEditFood = { index ->
                            editingFoodIndex = index
                            editingFoodItem = state.recognizedFoods[index]
                            showFoodEditor = true
                        },
                        onRemoveFood = { viewModel.removeFoodItem(it) },
                        onAddFood = {
                            editingFoodIndex = -1
                            editingFoodItem = null
                            showFoodEditor = true
                        },
                    )
                }

                // 保存按钮
                item(key = "save_button") {
                    Button(
                        onClick = {
                            viewModel.saveRecord(userNote)
                            userNote = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.recognizedFoods.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                    ) {
                        Text("保存记录", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item(key = "divider") { HorizontalDivider() }

            // 今日汇总
            item(key = "today_summary") {
                TodaySummarySection(
                    todayCalories = state.todayTotalCalories,
                    todayRecords = state.todayRecords,
                    recommendedCalories = state.recommendedCalories,
                    onDeleteRecord = viewModel::deleteRecord,
                )
            }
        }
    }
}

@Composable
private fun MealTypeSelector(
    selectedMealType: MealType,
    onMealTypeSelected: (MealType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MealType.entries.forEach { mealType ->
            FilterChip(
                selected = selectedMealType == mealType,
                onClick = { onMealTypeSelected(mealType) },
                label = { Text(mealType.displayName, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@Composable
private fun ImagePickerSection(
    imageUri: Uri?,
    hasBitmap: Boolean,
    capturedBitmap: Bitmap?,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onClearImage: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        if (imageUri != null) {
            // 已选图片预览：两阶段降采样解码（bounds + inSampleSize），预览区只要约 1024px，
            // 相机原图全尺寸解码会有几十 MB 的内存峰值
            Box(modifier = Modifier.fillMaxWidth()) {
                val context = LocalContext.current
                var imageBitmap by remember(imageUri) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(imageUri) {
                    imageBitmap = withContext(Dispatchers.IO) {
                        val bitmap = runCatching { ImageCompressor.decodeScaled(context, imageUri) }
                            .onFailure { android.util.Log.w("DietRecord", "Failed to decode preview ${imageUri}", it) }
                            .getOrNull()
                        bitmap?.asImageBitmap()
                    }
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("图片加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(
                    onClick = onClearImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            CircleShape
                        ),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "移除图片", modifier = Modifier.size(18.dp))
                }
            }
        } else if (hasBitmap) {
            // 拍照预览（Bitmap from ViewModel）
            Box(modifier = Modifier.fillMaxWidth()) {
                val validBitmap = capturedBitmap?.takeIf { !it.isRecycled }
                // 包装实例 memo 化，避免每次重组新建 ImageBitmap 导致 Image 重绘
                val capturedImage = remember(validBitmap) { validBitmap?.asImageBitmap() }
                if (capturedImage != null) {
                    Image(
                        bitmap = capturedImage,
                        contentDescription = "拍摄的食物图片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("照片已拍摄", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                IconButton(
                    onClick = onClearImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            CircleShape
                        ),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "移除图片", modifier = Modifier.size(18.dp))
                }
            }
        } else {
            // 未选图片：拍照/相册按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onTakePhoto,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("拍照")
                }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(
                    onClick = onPickFromGallery,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("相册")
                }
            }
        }
    }
}

@Composable
private fun AiResultSection(
    trafficLight: String,
    aiAdvice: String,
    foods: List<RecognizedFoodItem>,
    totalCalories: Int,
    macros: com.example.weight.data.diet.Macros,
    onEditFood: (Int) -> Unit,
    onRemoveFood: (Int) -> Unit,
    onAddFood: () -> Unit,
) {
    // 交通灯
    val lightColor = TrafficLightColorMap[trafficLight] ?: Color.Gray
    val lightLabel = TrafficLightLabelMap[trafficLight] ?: "未知"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 交通灯 + 总热量
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(lightColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        lightLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = lightColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "${totalCalories} kcal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            // 宏量营养素
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

            // AI 建议
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

            // 食物列表
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
                        Text(
                            "${food.estimatedGrams}g · ${food.estimatedCalories}kcal${if (food.category.isNotBlank()) " · ${food.category}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row {
                        IconButton(
                            onClick = { onEditFood(index) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "编辑",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(
                            onClick = { onRemoveFood(index) },
                            modifier = Modifier.size(32.dp),
                        ) {
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

            // 添加食物按钮
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
private fun TodaySummarySection(
    todayCalories: Int,
    todayRecords: List<DietRecord>,
    recommendedCalories: Int?,
    onDeleteRecord: (DietRecord) -> Unit,
) {
    // 枚举名 → 展示项的 O(1) 查表，避免每条记录渲染时线性扫描枚举
    val mealTypeByName = remember { MealType.entries.associateBy { it.name } }
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

            if (recommendedCalories != null && recommendedCalories > 0) {
                IntakeProgressContent(intake = todayCalories, recommended = recommendedCalories)
            } else {
                Text(
                    "已摄入: $todayCalories kcal",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "补全设置中的年龄、性别与活动水平后，可获得个性化建议摄入量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (todayRecords.isEmpty()) {
                Text(
                    "暂无记录，拍张照片开始吧！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                todayRecords.forEach { record ->
                    val lightColor = TrafficLightColorMap[record.trafficLight] ?: Color.Gray
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
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
                            Column {
                                Text(
                                    mealTypeByName[record.mealType]?.displayName ?: record.mealType,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (record.userInput.isNotBlank()) {
                                    Text(
                                        record.userInput,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            IconButton(
                                onClick = { onDeleteRecord(record) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 已摄入/建议摄入进度条：进度封顶 100%，超支用状态色与文案表达 */
@Composable
private fun IntakeProgressContent(intake: Int, recommended: Int) {
    val status = CalorieCalculator.intakeStatus(intake, recommended)
    val statusColor = when (status) {
        IntakeStatus.ENOUGH -> TrafficLightColorMap.getValue("GREEN")
        IntakeStatus.NEAR_LIMIT -> TrafficLightColorMap.getValue("YELLOW")
        IntakeStatus.OVER -> TrafficLightColorMap.getValue("RED")
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
