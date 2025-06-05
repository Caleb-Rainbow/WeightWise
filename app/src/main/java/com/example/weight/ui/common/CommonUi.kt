package com.example.weight.ui.common

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import java.util.Calendar
import java.util.Locale

@Composable
fun RequiredText(modifier: Modifier = Modifier, text: String, fontSize: TextUnit = 14.sp) {
    Text(modifier = modifier, text = buildAnnotatedString {
        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
            append("*")
        }
        append(text)
    }, fontSize = fontSize)
}

@Composable
fun TimePickerOutlineTextFiled(
    modifier: Modifier = Modifier,
    hint: String,
    value: String,
    onValueChanged: (String) -> Unit,
    isRequired: Boolean,
    singleLine: Boolean = false
) {
    var isShowTimePicker by remember { mutableStateOf(false) }
    if (isShowTimePicker) {
        TimePicker(onDismiss = { isShowTimePicker = false }, onConfirm = {
            onValueChanged(it)
            isShowTimePicker = false
        })
    }
    RequiredOutlinedTextField(
        modifier = modifier,
        hint = hint,
        value = value,
        onValueChanged = onValueChanged,
        isRequired = isRequired,
        readOnly = true,
        singleLine = singleLine,
        trailingIcon = {
            IconButton(onClick = { isShowTimePicker = true }) {
                Icon(imageVector = Icons.Default.Schedule, contentDescription = "选择时间")
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePicker(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentTime = Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(Calendar.MINUTE),
        is24Hour = true,
    )

    TimePickerDialog(
        onDismiss = { onDismiss() },
        onConfirm = { onConfirm(String.format(Locale.CHINA, "%02d:%02d", timePickerState.hour, timePickerState.minute)) }) {
        TimePicker(
            state = timePickerState,
        )
    }
}

@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit, onConfirm: () -> Unit, content: @Composable () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, title = {
        Text("选择时间")
    }, dismissButton = {
        TextButton(onClick = { onDismiss() }) {
            Text("取消")
        }
    }, confirmButton = {
        TextButton(onClick = { onConfirm() }) {
            Text("确认")
        }
    }, text = { content() })
}

/**
 * 带有必选符号的输入框
 * @param hint 提示文字
 * @param isRequired 是否必选
 * @param value 输入框的值
 * @param onValueChanged 输入框的值改变的回调
 * */
@Composable
fun RequiredOutlinedTextField(
    modifier: Modifier = Modifier,
    hint: String,
    isRequired: Boolean,
    value: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    onValueChanged: (String) -> Unit = {}
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        label = {
            Text(text = buildAnnotatedString {
                if (isRequired) {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                        append("*")
                    }
                }
                append(hint)
            }, fontSize = 14.sp)
        },
        textStyle = TextStyle.Default.copy(fontSize = 14.sp),
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        readOnly = readOnly,
        singleLine = singleLine,
        onValueChange = {
            try {
                onValueChanged(it)
            } catch (e: NumberFormatException) {
                e.message?.let { it1 -> Log.e("NumberFormatException", it1) }
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDocked(
    modifier: Modifier = Modifier,
    selectedDate: String,
    isRequired: Boolean = false,
    datePickerState: DatePickerState,
    iconTintColor: Color = LocalContentColor.current,
    label: String = "选择日期",
    isReadOnly: Boolean = false,
    onDetermine: () -> Unit = {}
) {
    var showDatePicker by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
    ) {
        RequiredOutlinedTextField(
            modifier = modifier,
            value = selectedDate,
            onValueChanged = { },
            hint = label,
            isRequired = isRequired,
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = {
                    if (!isReadOnly) showDatePicker = !showDatePicker
                }) {
                    Icon(
                        imageVector = Icons.Default.DateRange, tint = iconTintColor, contentDescription = label
                    )
                }
            })
        if (showDatePicker) {
            Popup(
                onDismissRequest = { showDatePicker = false }, alignment = Alignment.TopStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 64.dp)
                        .shadow(elevation = 5.dp)
                ) {
                    DatePicker(
                        title = null, headline = null, state = datePickerState, showModeToggle = false
                    )
                    TextButton(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp), onClick = {
                            showDatePicker = false
                            onDetermine()
                        }) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseOutlineTextFieldDropdownMenu(
    modifier: Modifier = Modifier,
    dropdownMenuModifier: Modifier = Modifier,
    hint: String,
    isExpend: Boolean,
    description: String,
    onValueChanged: (String) -> Unit = {},
    isRequired: Boolean = false,
    readOnly: Boolean = true,
    isNoExpend: Boolean,
    iconColor: Color = LocalContentColor.current,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    BaseDropdownMenu(
        modifier = modifier,
        dropdownMenuModifier = dropdownMenuModifier,
        isExpend = isExpend,
        onExpandedChange = { if (!isNoExpend) onExpandedChange(it) },
        textFiled = {

            OutlinedTextField(
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                value = description,
                enabled = !isNoExpend,
                singleLine = true,
                readOnly = readOnly,
                label = {
                    Text(text = buildAnnotatedString {
                        if (isRequired) {
                            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.error)) {
                                append("*")
                            }
                        }
                        append(hint)
                    })
                },
                onValueChange = onValueChanged,
                trailingIcon = {
                    Icon(
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable)
                            .rotate(if (isExpend) 180f else 0f),
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = iconColor
                    )
                },
                colors = colors
            )
        }) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseDropdownMenu(
    modifier: Modifier = Modifier,
    dropdownMenuModifier: Modifier = Modifier,
    isExpend: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    textFiled: @Composable ExposedDropdownMenuBoxScope.() -> Unit,
    content: @Composable () -> Unit
) {
    ExposedDropdownMenuBox(
        modifier = modifier.defaultMinSize(
            minWidth = OutlinedTextFieldDefaults.MinWidth, minHeight = OutlinedTextFieldDefaults.MinHeight
        ), expanded = isExpend, onExpandedChange = { onExpandedChange(it) }) {
        textFiled()
        ExposedDropdownMenu(
            modifier = dropdownMenuModifier,
            expanded = isExpend,
            shape = RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp),
            onDismissRequest = { onExpandedChange(false) },
            containerColor = Color.White
        ) {
            content()
        }
    }
}

@Composable
fun <T> ExposedOutlineTextFieldGenericListDropdownMenu(
    modifier: Modifier = Modifier,
    dropdownMenuModifier: Modifier = Modifier,
    hint: String,
    value: String? = null,
    iconColor: Color = LocalContentColor.current,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    initialDescription: String = "",
    isRequired: Boolean = false,
    descriptionList: List<String>,
    genericList: List<T>,
    onSelected: (T) -> Unit,
    isNoExpend: Boolean = false
) {
    var description by remember {
        mutableStateOf(initialDescription)
    }
    var isExpend by remember {
        mutableStateOf(false)
    }
    BaseOutlineTextFieldDropdownMenu(
        modifier = modifier,
        dropdownMenuModifier = dropdownMenuModifier,
        isExpend = isExpend,
        hint = hint,
        description = value ?: description,
        isRequired = isRequired,
        isNoExpend = isNoExpend,
        colors = colors,
        iconColor = iconColor,
        onExpandedChange = { isExpend = it }) {
        descriptionList.forEachIndexed { index, s ->
            DropdownMenuItem(text = { Text(text = s) }, onClick = {
                isExpend = false
                description = s
                onSelected(genericList[index])
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTopBar(
    modifier: Modifier = Modifier,
    title: String,
    actions: @Composable (RowScope.() -> Unit) = {},
    goBack: () -> Unit,
    isShowBackArrow: Boolean = true
) {
    TopAppBar(title = {
        Text(text = title, fontSize = 14.sp)
    },
        modifier = modifier,
        actions = actions,
        navigationIcon = {
            if (isShowBackArrow) {
                IconButton(onClick = goBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = null
                    )
                }
            }
        }
    )
}
