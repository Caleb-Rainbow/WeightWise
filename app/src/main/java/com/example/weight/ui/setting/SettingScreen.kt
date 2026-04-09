package com.example.weight.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.LocalStorageData
import com.example.weight.ui.common.MyTopBar
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(modifier: Modifier = Modifier, goBack: () -> Unit) {
    Scaffold(modifier = modifier, topBar = {
        MyTopBar(title = "设置", goBack = goBack)
    }) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(horizontal = 15.dp)) {
            val height by LocalStorageData.height.collectAsStateWithLifecycle()
            val targetWeight by LocalStorageData.targetWeight.collectAsStateWithLifecycle()
            val completeDays by LocalStorageData.completeDays.collectAsStateWithLifecycle()
            OutlinedTextField(value = height.toString(), onValueChange = {
                it.toDoubleOrNull()?.let {change->
                    LocalStorageData.height.update {
                        change
                    }
                }
            }, label = {
                Text("身高(cm)")
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = targetWeight.toString(), onValueChange = {
                it.toDoubleOrNull()?.let {change->
                    LocalStorageData.targetWeight.update {
                        change
                    }
                }
            }, label = {
                Text("目标体重(kg)")
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = completeDays.toString(), onValueChange = {
                it.toIntOrNull()?.let {change->
                    LocalStorageData.completeDays.update {
                        change
                    }
                }
            }, label = {
                Text("计划完成天数")
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
    }
}