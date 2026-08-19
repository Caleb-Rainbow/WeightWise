package com.example.weight.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * 可自由编辑的数字输入框。
 *
 * 输入框必须绑定本地文本状态以保留 ""、"17." 这类中间态，只在文本能解析为
 * 数字时才把值同步给 [onValueChange]；若直接绑定 value.toString()，删除到空串
 * 或输入小数点的中间态时解析失败、状态不更新，输入框会回显旧值导致内容删不掉。
 */
@Composable
fun NumberTextField(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value.formatPlain()) }
    var lastEmitted by remember { mutableStateOf(value) }

    // 外部值变化（初始加载或别处修改）时回写文本；与 lastEmitted 相同说明是自己
    // 刚上报的值，跳过，避免打断 "1." 之类尚未解析完成的中间态
    LaunchedEffect(value) {
        if (value != lastEmitted) {
            text = value.formatPlain()
            lastEmitted = value
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            newText.toDoubleOrNull()?.let {
                lastEmitted = it
                onValueChange(it)
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** 整数值去掉小数尾巴，如 170.0 显示为 "170" */
private fun Double.formatPlain(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
