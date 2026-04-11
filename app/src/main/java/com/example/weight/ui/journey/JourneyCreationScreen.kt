package com.example.weight.ui.journey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weight.ui.common.MyTopBar
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyCreationScreen(
    goBack: () -> Unit,
    onJourneyCreated: () -> Unit,
    viewModel: JourneyCreationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.createdJourneyId != null) {
        onJourneyCreated()
        return
    }

    Scaffold(
        topBar = {
            MyTopBar(
                title = "开启减重旅程",
                goBack = goBack,
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "设定你的目标，AI将为你量身定制分阶段运动计划",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 起始体重
            OutlinedTextField(
                value = state.startWeight,
                onValueChange = viewModel::updateStartWeight,
                label = { Text("起始体重 (kg)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isCreating,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 目标体重
            OutlinedTextField(
                value = state.targetWeight,
                onValueChange = viewModel::updateTargetWeight,
                label = { Text("目标体重 (kg)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isCreating,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 目标天数
            val targetDays = state.targetDays.toIntOrNull() ?: 90
            Text(
                text = "目标天数：${targetDays}天",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = targetDays.toFloat(),
                onValueChange = { viewModel.updateTargetDays(it.roundToInt().toString()) },
                valueRange = 14f..365f,
                enabled = !state.isCreating,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("14天", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("365天", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 预估阶段预览
            PhasePreviewCard(targetDays = targetDays)

            Spacer(modifier = Modifier.height(24.dp))

            // 错误提示
            if (state.error != null) {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 创建按钮
            Button(
                onClick = viewModel::createJourney,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCreating,
                shape = RoundedCornerShape(12.dp),
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.isCreating) "正在生成计划..." else "开启旅程")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PhasePreviewCard(targetDays: Int) {
    val phase1End = (targetDays * 0.14).roundToInt().coerceAtLeast(7)
    val phase3Start = (targetDays * 0.80).roundToInt().coerceAtMost(targetDays - 6)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "预估阶段划分",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            PhasePreviewRow(
                icon = Icons.Default.SelfImprovement,
                name = "适应期",
                dayRange = "第1-${phase1End}天",
                description = "建立运动习惯",
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            PhasePreviewRow(
                icon = Icons.Default.LocalFireDepartment,
                name = "燃脂期",
                dayRange = "第${phase1End + 1}-${phase3Start}天",
                description = "高效燃烧脂肪",
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            PhasePreviewRow(
                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                name = "塑形期",
                dayRange = "第${phase3Start + 1}-${targetDays}天",
                description = "巩固塑形",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PhasePreviewRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    dayRange: String,
    description: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "$name · $dayRange",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
