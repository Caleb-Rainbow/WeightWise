package com.example.weight.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.data.LocalStorageData
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.ui.common.BMI
import com.example.weight.ui.common.BMIIndexChart
import java.text.DecimalFormat

@Composable
fun BMIContent(modifier: Modifier, record: DailyMinWeight?,bmi: Double) {

    val bmiLeave by remember(bmi) {
        mutableStateOf(BMI.fromBMIValue(bmi))
    }
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BMIDataItem(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 20.dp),
                    title = "BMI",
                    content = DecimalFormat("0.0").format(bmi)
                )
                VerticalDivider(Modifier.height(40.dp))
                BMIDataItem(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 20.dp), title = "评级", content = bmiLeave?.label ?: "无"
                )
            }
            BMIIndexChart(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .padding(top = 10.dp), currentBMI = bmi
            )
        }
    }
}

@Composable
private fun BMIDataItem(modifier: Modifier, title: String, content: String) {
    Column(modifier = modifier) {
        Text(text = content, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = title, fontSize = 12.sp, color = Color.Gray)
    }
}
