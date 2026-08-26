package com.example.weight.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.example.weight.data.record.DailyWeight
import com.example.weight.ui.common.BMI
import com.example.weight.ui.common.BMIIndexChart
import com.example.weight.ui.common.bmiColor
import java.text.DecimalFormat

@Composable
fun BMIContent(modifier: Modifier, record: DailyWeight?, bmi: Double) {

    val bmiLeave by remember(bmi) {
        mutableStateOf(BMI.fromBMIValue(bmi))
    }
    val bmiFormat = remember { DecimalFormat("0.0") }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("BMI INDEX", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.58f))
                    Text(bmiFormat.format(bmi), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                }
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text("当前评级", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(bmiLeave?.label ?: "无", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            BMIIndexChart(
                modifier = Modifier.padding(top = 12.dp), currentBMI = bmi
            )
            // 无选中记录（bmi=0）时 fromBMIValue 返回 null，不显示解读
            bmiLeave?.let { leave ->
                Text(
                    text = leave.advice,
                    style = MaterialTheme.typography.bodySmall,
                    color = bmiColor(leave),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
