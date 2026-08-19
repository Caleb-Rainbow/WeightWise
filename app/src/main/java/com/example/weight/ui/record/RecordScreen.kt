package com.example.weight.ui.record

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.record.Record
import com.example.weight.ui.common.DeleteDialog
import com.example.weight.ui.common.MyTopBar
import com.example.weight.ui.main.AddRecordDialog
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt

/** 体重下降（利好）与上涨的展示色，和饮食记录页的交通灯配色保持一致 */
private val DecreaseColor = Color(0xFF4CAF50)
private val IncreaseColor = Color(0xFFEF5350)

@Composable
fun RecordScreen(
    modifier: Modifier = Modifier,
    viewModel: RecordViewModel = koinViewModel(),
    goBack: () -> Unit = {}
) {
    val recordList = viewModel.recordPager.collectAsLazyPagingItems()
    val latestRecord by viewModel.latestRecord.collectAsStateWithLifecycle(initialValue = null)
    val recordCount by viewModel.recordCount.collectAsStateWithLifecycle(initialValue = 0)
    val snackBarShow = LocalSnackBarShow.current

    var deleteTarget by remember { mutableStateOf<Record?>(null) }
    var editTarget by remember { mutableStateOf<Record?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    deleteTarget?.let { target ->
        DeleteDialog(
            onDismissRequest = { deleteTarget = null },
            onConfirmClick = {
                viewModel.deleteRecord(target)
                deleteTarget = null
                snackBarShow("已删除该记录")
            },
            content = "请确认是否要删除『${TimeUtils.convertMillisToTime(target.timestamp)}的记录』"
        )
    }
    editTarget?.let { target ->
        EditRecordDialog(
            record = target,
            onDismissRequest = { editTarget = null },
            onConfirm = { updated ->
                viewModel.updateRecord(updated)
                editTarget = null
                snackBarShow("修改成功")
            }
        )
    }
    if (showAddDialog) {
        AddRecordDialog(onDismissRequest = { showAddDialog = false })
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            MyTopBar(
                title = "记录",
                goBack = goBack,
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "添加记录")
                    }
                })
        }) { paddingValues ->
        val refreshState = recordList.loadState.refresh
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when {
                refreshState is LoadState.Loading && recordList.itemCount == 0 -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                recordList.itemCount == 0 -> EmptyRecordsContent()

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "summary") {
                            RecordSummaryContent(
                                latestRecord = latestRecord,
                                recordCount = recordCount
                            )
                        }
                        item(key = "swipe_hint") {
                            Text(
                                text = "提示：左滑记录可编辑或删除",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        items(recordList.itemCount, key = recordList.itemKey { it.id }) { index ->
                            recordList[index]?.let { record ->
                                // 列表按时间倒序，index+1 是时间上更早的一条，用于计算涨跌；
                                // LazyPagingItems.get 越界会抛异常，必须先判断边界
                                val hasOlderLoaded = index + 1 < recordList.itemCount
                                val appendState = recordList.loadState.append
                                val isEndOfPagination =
                                    appendState is LoadState.NotLoading && appendState.endOfPaginationReached
                                SwipeableRecordItem(
                                    modifier = Modifier.animateItem(),
                                    onEdit = { editTarget = record },
                                    onDelete = { deleteTarget = record }
                                ) {
                                    RecordItemContent(
                                        record = record,
                                        previousWeight = if (hasOlderLoaded) {
                                            recordList[index + 1]?.weight
                                        } else null,
                                        isOldestRecord = !hasOlderLoaded && isEndOfPagination
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRecordsContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无体重记录",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "点击右上角 + 添加第一条记录吧",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RecordSummaryContent(latestRecord: Record?, recordCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryItem(
                modifier = Modifier.weight(1f),
                value = latestRecord?.let { DecimalFormat("#.#").format(it.weight) } ?: "--",
                unit = "kg",
                label = "最新体重"
            )
            VerticalDivider(modifier = Modifier.height(26.dp))
            SummaryItem(
                modifier = Modifier.weight(1f),
                value = "$recordCount",
                unit = "次",
                label = "累计记录"
            )
            VerticalDivider(modifier = Modifier.height(26.dp))
            SummaryItem(
                modifier = Modifier.weight(1f),
                value = latestRecord?.let { TimeUtils.convertMillisToDate(it.timestamp) } ?: "--",
                unit = "",
                label = "最近记录"
            )
        }
    }
}

@Composable
private fun SummaryItem(
    modifier: Modifier,
    value: String,
    unit: String,
    label: String
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = " $unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun RecordItemContent(
    record: Record,
    previousWeight: Double?,
    isOldestRecord: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DateBlock(timestamp = record.timestamp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = TimeUtils.convertMillisToHM(record.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (record.log.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = record.log,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = DecimalFormat("#.#").format(record.weight),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " kg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                WeightChangeText(
                    weight = record.weight,
                    previousWeight = previousWeight,
                    isOldestRecord = isOldestRecord
                )
            }
        }
    }
}

/** 记录卡片左侧的日期块 */
@Composable
private fun DateBlock(timestamp: Long) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = TimeUtils.convertMillisToMonth(timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = TimeUtils.convertMillisToDay(timestamp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * 与上一条记录相比的涨跌标识。
 * previousWeight 为空且已到分页末尾时是真正的首条记录；
 * 为空但还有更早数据未加载时不展示，避免误标
 */
@Composable
private fun WeightChangeText(
    weight: Double,
    previousWeight: Double?,
    isOldestRecord: Boolean
) {
    if (previousWeight == null) {
        Text(
            text = if (isOldestRecord) "首次记录" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        return
    }
    val delta = weight - previousWeight
    val format = remember { DecimalFormat("#.#") }
    when {
        delta < 0 -> WeightChangeBadge(text = "↓ ${format.format(abs(delta))}", color = DecreaseColor)
        delta > 0 -> WeightChangeBadge(text = "↑ ${format.format(delta)}", color = IncreaseColor)
        else -> WeightChangeBadge(text = "持平", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun WeightChangeBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Medium
    )
}

/** 单个侧滑操作的宽度 */
private val SwipeActionWidth = 76.dp

/**
 * 可侧滑的记录项容器：左滑露出「编辑 / 删除」操作层，
 * 松手后按滑动速度或越过一半的距离决定展开/收起
 */
@Composable
private fun SwipeableRecordItem(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val actionsWidthPx = with(LocalDensity.current) { (SwipeActionWidth * 2).toPx() }
    val offsetX = remember { Animatable(0f) }
    val draggableState = remember {
        DraggableState { delta ->
            coroutineScope.launch {
                offsetX.snapTo((offsetX.value + delta).coerceIn(-actionsWidthPx, 0f))
            }
        }
    }
    val close = {
        coroutineScope.launch { offsetX.animateTo(0f, animationSpec = tween(250)) }
    }

    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
        // 背景操作层：按钮必须靠右排布，左滑露出卡片右侧时才能看到
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End
        ) {
            SwipeActionBox(
                icon = Icons.Default.Edit,
                label = "编辑",
                color = MaterialTheme.colorScheme.primary,
                onClick = {
                    close()
                    onEdit()
                }
            )
            SwipeActionBox(
                icon = Icons.Default.Delete,
                label = "删除",
                color = MaterialTheme.colorScheme.error,
                onClick = {
                    close()
                    onDelete()
                }
            )
        }
        // 前景内容层
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = draggableState,
                    onDragStopped = { velocity ->
                        coroutineScope.launch {
                            val velocityThreshold = 500f
                            val targetOffsetX = if (abs(velocity) > velocityThreshold) {
                                if (velocity < 0) -actionsWidthPx else 0f
                            } else {
                                if (offsetX.value < -actionsWidthPx / 2) -actionsWidthPx else 0f
                            }
                            offsetX.animateTo(targetValue = targetOffsetX, animationSpec = tween(250))
                        }
                    }
                )
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeActionBox(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(SwipeActionWidth)
            .fillMaxHeight()
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Text(text = label, color = Color.White, fontSize = 12.sp)
        }
    }
}
