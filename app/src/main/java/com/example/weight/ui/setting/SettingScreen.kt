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
import com.example.weight.data.LocalStorageData
import com.example.weight.ui.common.MyTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(modifier: Modifier = Modifier, goBack: () -> Unit) {
    Scaffold(modifier = modifier, topBar = {
        MyTopBar(title = "设置", goBack = goBack)
    }) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            var height by remember {
                mutableDoubleStateOf(LocalStorageData.height)
            }
            OutlinedTextField(value = height.toString(), onValueChange = {
                it.toDoubleOrNull()?.let {
                    height = it
                }
            }, label = {
                Text("身高(cm)")
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
    }
}