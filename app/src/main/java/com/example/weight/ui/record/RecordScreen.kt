package com.example.weight.ui.record

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.weight.data.record.Record
import com.example.weight.ui.common.DeleteDialog
import com.example.weight.ui.common.MyTopBar
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun RecordScreen(modifier: Modifier = Modifier, viewModel: RecordViewModel = koinViewModel(), goBack:()-> Unit = {}){
    Scaffold(modifier = modifier, topBar = {
        MyTopBar(title = "记录", goBack = goBack)
    }) {paddingValues->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            val recordList = viewModel.recordPager.collectAsLazyPagingItems()
            LazyColumn(modifier = Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)){
                items(recordList.itemCount, key = recordList.itemKey{it.id}) {
                    recordList[it]?.let {
                        RecordItem(modifier = Modifier.animateItem(), record = it){
                            viewModel.deleteRecord(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordItem(modifier: Modifier,record: Record,onDelete:()-> Unit){
    val format = remember {
        DecimalFormat("#.#kg")
    }
    var isShowDeleteDialog by remember {
        mutableStateOf(false)
    }
    if (isShowDeleteDialog){
        DeleteDialog(onDismissRequest = {
            isShowDeleteDialog = false
        }, onConfirmClick = {
            isShowDeleteDialog = false
            onDelete()
        }, content = "请确认是否要删除『${TimeUtils.convertMillisToTime(record.timestamp)}的记录』")
    }
    Card(modifier = modifier, onClick = {
        isShowDeleteDialog = true
    }) {
        Row (modifier = Modifier.padding(8.dp)){
            Text(text = TimeUtils.convertMillisToTime(record.timestamp))
            Spacer(Modifier.weight(1f))
            Text(text = format.format(record.weight))
        }
    }
}


// 数据类，代表列表中的一个项目
data class ListItem(val id: Int, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableLazyColumnScreen() {
    // 使用 mutableStateListOf 以便在删除时 Compose 能够正确响应列表变化
    val items = remember {
        mutableStateListOf(
            ListItem(1, "快速向左滑动来打开"),
            ListItem(2, "慢速滑动则根据距离判断"),
            ListItem(3, "这是第三个项目"),
            ListItem(4, "Jetpack Compose 真棒"),
            ListItem(5, "这是第五个项目"),
            ListItem(6, "你可以编辑或删除"),
            ListItem(7, "第七个幸运数字"),
            ListItem(8, "最后一个项目了"),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("侧滑操作列表") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(items, key = { it.id }) { item ->
                // 对每个项目应用 SwipeableItem
                SwipeableItem(
                    onDelete = {
                        // 从列表中移除项目
                        items.remove(item)
                    },
                    onEdit = {
                        // 处理编辑逻辑，例如弹出一个对话框
                        println("Editing item ${item.id}")
                    }
                ) {
                    // 这是每个列表项的内容
                    ListItemContent(item.text)
                }
            }
        }
    }
}

/**
 * 可侧滑的列表项容器
 * @param onDelete 删除操作的回调
 * @param onEdit 编辑操作的回调
 * @param content 列表项的主体内容
 */
@Composable
fun SwipeableItem(
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // 将 dp 转换为像素，用于计算滑动距离
    val actionsWidth = with(LocalDensity.current) { (160.dp).toPx() }

    // 使用 Animatable 来控制偏移量，实现平滑动画
    val offsetX = remember { Animatable(0f) }

    // 【改动点 1】创建 DraggableState 来处理拖动中的偏移量更新
    val draggableState = remember {
        DraggableState { delta ->
            // 在拖动时，根据拖动量 delta 更新 offsetX 的值
            // 并使用 coerceIn 限制偏移量在 [-actionsWidth, 0f] 的范围内
            coroutineScope.launch {
                val newOffset = (offsetX.value + delta).coerceIn(-actionsWidth, 0f)
                offsetX.snapTo(newOffset)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer) // 背景颜色
    ) {
        // 背景层：编辑和删除按钮 (无改动)
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    onEdit()
                    // 点击后恢复原位
                    coroutineScope.launch {
                        offsetX.animateTo(0f, animationSpec = tween(300))
                    }
                },
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .background(Color.Gray)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Color.White)
                    Text("编辑", color = Color.White)
                }
            }
            IconButton(
                onClick = {
                    // 直接调用删除，因为项目会被移除，不需要动画恢复
                    onDelete()
                },
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.White)
                    Text("删除", color = Color.White)
                }
            }
        }

        // 前景层：列表项的主要内容
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                // 【改动点 2】使用 draggable 修饰符替换 pointerInput
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = draggableState,
                    // onDragStopped 在拖动结束时被调用，并提供了速度 (velocity)
                    onDragStopped = { velocity ->
                        coroutineScope.launch {
                            // 【改动点 3】加入速度判断逻辑
                            // 这个阈值可以根据需要进行调整
                            val velocityThreshold = 500f
                            val targetOffsetX = if (abs(velocity) > velocityThreshold) {
                                // 如果速度超过阈值（快速滑动）
                                // 速度为负表示向左滑，展开；速度为正表示向右滑，关闭
                                if (velocity < 0) -actionsWidth else 0f
                            } else {
                                // 如果是慢速拖动，则使用原来的距离判断逻辑
                                if (offsetX.value < -actionsWidth / 2) -actionsWidth else 0f
                            }
                            // 动画到目标位置
                            offsetX.animateTo(
                                targetValue = targetOffsetX,
                                animationSpec = tween(durationMillis = 300)
                            )
                        }
                    }
                )
                .background(MaterialTheme.colorScheme.surface) // 设置背景色以遮挡下面的按钮
        ) {
            content()
        }
    }
}

/**
 * 列表项内容的简单示例
 * @param text 要显示的文本
 */
@Composable
fun ListItemContent(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    HorizontalDivider() // 添加分割线
}