package com.example.weight.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

@Composable
fun DeleteDialog(
    title:String = "删除",
    content:String = "请确认是否要删除",
    onDismissRequest: () -> Unit,
    onConfirmClick: () -> Unit,
    initialDelay: Int = 1
) {
    var countDown by remember { mutableIntStateOf(initialDelay) }
    var isButtonEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(countDown) {
        if (countDown > 0) {
            delay(1000)
            countDown--
        } else {
            isButtonEnabled = true
        }
    }
    AlertDialog(onDismissRequest = onDismissRequest, title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text =title)
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }, text = {
        Text(text = content)
    }, confirmButton = {
        TextButton(enabled = isButtonEnabled, onClick = onConfirmClick) {
            Text(
                text = if (isButtonEnabled) "确认" else "确认(${countDown}秒)",
                color = if (isButtonEnabled) MaterialTheme.colorScheme.error else Color.Gray
            )
        }
    }, dismissButton = {
        TextButton(onClick = onDismissRequest) {
            Text(text = "取消")
        }
    })
}
