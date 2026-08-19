package com.example.weight.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.weight.data.record.DailyMinWeight
import com.example.weight.ui.common.BMI
import com.example.weight.ui.common.BMIIndexChart
import java.text.DecimalFormat

@Composable
fun BMIContent(modifier: Modifier, record: DailyMinWeight?, bmi: Double) {

    val bmiLeave by remember(bmi) {
        mutableStateOf(BMI.fromBMIValue(bmi))
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BMIDataItem(
                    modifier = Modifier.weight(1f),
                    title = "BMI",
                    content = DecimalFormat("0.0").format(bmi)
                )
                VerticalDivider(Modifier.height(40.dp))
                BMIDataItem(
                    modifier = Modifier.weight(1f),
                    title = "评级",
                    content = bmiLeave?.label ?: "无"
                )
            }
            BMIIndexChart(
                modifier = Modifier.padding(top = 12.dp), currentBMI = bmi
            )
        }
    }
}

@Composable
private fun BMIDataItem(modifier: Modifier, title: String, content: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = content,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
