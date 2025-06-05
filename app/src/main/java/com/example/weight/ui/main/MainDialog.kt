package com.example.weight.ui.main

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.LocalSnackBarShow
import com.example.weight.data.LocalStorageData
import com.example.weight.ui.common.DatePickerDocked
import com.example.weight.ui.common.TimePickerOutlineTextFiled
import com.example.weight.util.TimeUtils
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.math.absoluteValue

@Composable
fun MainDialog( viewModel: MainViewModel = koinViewModel()){
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    if (dialogState.isShowAddDialog) {
        AddRecordDialog(onDismissRequest = {
            viewModel.hideAddDialog()
        })
    }
    if (dialogState.isShowSetHeightDialog){
        SetHeightDialog(onDismissRequest = {
            viewModel.hideSetHeightDialog()
        })
    }
    LaunchedEffect(Unit) {
        if (LocalStorageData.height == 100.0){
            viewModel.showSetHeightDialog()
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordDialog(onDismissRequest: () -> Unit, viewModel: MainViewModel = koinViewModel()) {
    //日期-默认显示今日
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
    val date = pickerState.selectedDateMillis?.let {
        TimeUtils.convertMillisToDate(it)
    } ?: ""
    //时间-默认显示当前时间
    var time: String by remember {
        mutableStateOf(TimeUtils.getCurrentTimeFormat3())
    }
    //体重
    var weight by remember {
        mutableDoubleStateOf(50.0)
    }
    //获取上一次体重
    var initialWeight by remember {
        mutableStateOf<Double?>(null)
    }
    LaunchedEffect(Unit) {
        viewModel.getLastRecordWeight {
            initialWeight = it
        }
    }
    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Text("记录体重")
    }, text = {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                DatePickerDocked(
                    modifier = Modifier.weight(1f),
                    label = "日期",
                    isRequired = true,
                    datePickerState = pickerState,
                    selectedDate = date
                )
                Spacer(modifier = Modifier.width(10.dp))
                TimePickerOutlineTextFiled(modifier = Modifier.weight(0.7f), hint = "时间", value = time, onValueChanged = {
                    time = it
                }, isRequired = true, singleLine = true)
            }
            NumberSelector(integerList = remember { (50..120).toList() }, decimalList = remember {
                (0..9).toList()
            }, onWeightChange = {
                weight = it
            }, initialWeight = initialWeight, unit = "kg")
        }
    }, confirmButton = {
        val snackBarShow = LocalSnackBarShow.current
        Button(onClick = {
            viewModel.insertRecord(date, time, weight) {
                snackBarShow.invoke("添加成功")
                onDismissRequest()
            }
        }) {
            Text("添加")
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text("取消")
        }
    })
}

@Composable
fun SetHeightDialog(onDismissRequest: () -> Unit) {
    var height by remember {
        mutableDoubleStateOf(LocalStorageData.height)
    }
    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Text("选择身高")
    }, text = {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            NumberSelector(integerList = remember { (100..200).toList() }, decimalList = remember {
                (0..9).toList()
            }, onWeightChange = {
                height = it
            }, initialWeight = height, unit = "cm")
        }
    }, confirmButton = {
        Button(onClick = {
            LocalStorageData.height = height
            onDismissRequest()
        }) {
            Text("保存")
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text("取消")
        }
    }
    )
}

@Composable
private fun NumberSelector(
    integerList: List<Int>,
    decimalList: List<Int>,
    onWeightChange: (Double) -> Unit,
    initialWeight: Double? = null,
    unit: String
) {
    val integerPagerState = rememberPagerState { integerList.size }
    val decimalPagerState = rememberPagerState { decimalList.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(initialWeight) {
        initialWeight?.let {
            scope.launch {
                integerPagerState.animateScrollToPage(
                    it.toInt() - integerList[0],
                    animationSpec = spring(stiffness = Spring.StiffnessVeryLow)
                )
            }
            scope.launch {
                decimalPagerState.animateScrollToPage(
                    decimalList.indexOf((it % 1 * 10).toInt()),
                    animationSpec = spring(stiffness = Spring.StiffnessVeryLow)
                )
            }
        }
    }
    Row(modifier = Modifier.height(120.dp), verticalAlignment = Alignment.CenterVertically) {
        CustomVerticalPager(pagerState = integerPagerState, list = integerList) {
            val decimal = decimalList[decimalPagerState.currentPage] / 10.0
            onWeightChange(it + decimal)
        }
        Spacer(
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(color = Color.Gray)
        )
        CustomVerticalPager(pagerState = decimalPagerState, list = decimalList) {
            val integer = integerList[integerPagerState.currentPage]
            onWeightChange(integer + it.toDouble() / 10)
        }
        Text(modifier = Modifier.padding(start = 1.dp, bottom = 6.dp), text = unit, color = Color.Gray, fontSize = 14.sp)
    }
}

@Composable
private fun CustomVerticalPager(pagerState: PagerState, list: List<Int>, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    val vibrator = remember {
        context.getSystemService(Vibrator::class.java)
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            onChange(list[page])
        }
    }
    val fling = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(100),
    )
    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 100,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = 40.dp)
    ) { page ->
        Text(modifier = Modifier.graphicsLayer {
            val pageOffset = (
                    (pagerState.currentPage - page) + pagerState
                        .currentPageOffsetFraction
                    ).absoluteValue

            alpha = lerp(
                start = 0.5f,
                stop = 1f,
                fraction = 1f - pageOffset.coerceIn(0f, 1f)
            )
            val scale = lerp(
                start = 1.1f,
                stop = 0.8f,
                fraction = pageOffset.coerceIn(0f, 1f)
            )
            scaleX = scale
            scaleY = scale
        }, text = "${list[page]}", fontSize = 20.sp)
    }
}