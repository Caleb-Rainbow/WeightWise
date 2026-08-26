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

/** 从记录 JSON 解析食物并推导行标题/副标(食物名是视觉主角);解析失败回退通用文案 */
@Composable
internal fun rememberRecordTexts(record: DietRecord, mealLabel: String?): Pair<String, String> {
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
internal fun DietRecordRow(
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
internal fun HistoryTabPage(
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
            // T-5 趋势摘要:三数字+热力格,先于逐日时间线回答「最近记录得怎么样」
            item(key = "history_trend_summary") {
                HistoryTrendSummary(state = state)
            }
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
