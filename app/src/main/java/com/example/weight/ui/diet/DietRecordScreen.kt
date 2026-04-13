package com.example.weight.ui.diet

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.diet.DietRecord
import com.example.weight.data.diet.RecognizedFoodItem
import com.example.weight.ui.common.MyTopBar
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
    var showFoodEditor by remember { mutableStateOf(false) }
    var editingFoodIndex by remember { mutableStateOf(-1) }
    var editingFoodItem by remember { mutableStateOf<RecognizedFoodItem?>(null) }

    // 拍照
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { viewModel.onBitmapCaptured(it) }
    }

    // 相机权限请求
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            takePicture.launch(null)
        }
    }

    fun launchCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            takePicture.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
            item {
                MealTypeSelector(
                    selectedMealType = state.selectedMealType,
                    onMealTypeSelected = { viewModel.onMealTypeSelected(it) },
                )
            }

            // 图片选择区
            item {
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
            item {
                OutlinedTextField(
                    value = state.userNote,
                    onValueChange = { viewModel.onUserNoteChanged(it) },
                    label = { Text("添加备注（如：只吃了一半）") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // AI 识别按钮
            item {
                Button(
                    onClick = { viewModel.analyzeImage(context) },
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
            if (!state.hasCapturedBitmap && state.selectedImageUri == null && state.userNote.isNotBlank()) {
                item {
                    OutlinedButton(
                        onClick = { viewModel.analyzeTextOnly() },
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
                item {
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
                item {
                    Button(
                        onClick = { viewModel.saveRecord(context) },
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

            item { HorizontalDivider() }

            // 今日汇总
            item {
                TodaySummarySection(
                    todayCalories = state.todayTotalCalories,
                    todayRecords = state.todayRecords,
                    onDeleteRecord = { viewModel.deleteRecord(it) },
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
            // 已选图片预览
            Box(modifier = Modifier.fillMaxWidth()) {
                val context = LocalContext.current
                var imageBitmap by remember(imageUri) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(imageUri) {
                    withContext(Dispatchers.IO) {
                        try {
                            imageBitmap = context.contentResolver.openInputStream(imageUri)?.use { input ->
                                BitmapFactory.decodeStream(input)?.asImageBitmap()
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("DietRecord", "Failed to load image preview", e)
                        }
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
                if (validBitmap != null) {
                    Image(
                        bitmap = validBitmap.asImageBitmap(),
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
    onDeleteRecord: (DietRecord) -> Unit,
) {
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

            Text(
                "已摄入: $todayCalories kcal",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )

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
                                    MealType.entries.find { it.name == record.mealType }?.displayName ?: record.mealType,
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
